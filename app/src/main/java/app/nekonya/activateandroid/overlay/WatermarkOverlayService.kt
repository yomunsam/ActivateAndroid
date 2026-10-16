package app.nekonya.activateandroid.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.nekonya.activateandroid.ActivateApp
import app.nekonya.activateandroid.R
import app.nekonya.activateandroid.data.ModeLayoutSettings
import app.nekonya.activateandroid.data.WatermarkSettings
import app.nekonya.activateandroid.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground overlay service that can show a watermark on **multiple displays**
 * at once (phone primary + DeX / external desktop Dual Mode).
 *
 * Each active display gets its own TYPE_APPLICATION_OVERLAY via
 * [Context.createDisplayContext] + that display's [WindowManager].
 * If addView fails on one display (OEM quirk), the others are kept.
 */
class WatermarkOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Active overlays keyed by displayId. */
    private val overlays = LinkedHashMap<Int, OverlayEntry>()
    private var currentSettings: WatermarkSettings = WatermarkSettings()

    private var displayManager: DisplayManager? = null
    private val reattachRunnable = Runnable {
        rebuildOverlays(currentSettings)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "onDisplayAdded id=$displayId")
            scheduleRebuild("added:$displayId")
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "onDisplayRemoved id=$displayId")
            // Remove immediately so we don't keep a stale WM reference
            removeOverlay(displayId)
            scheduleRebuild("removed:$displayId")
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "onDisplayChanged id=$displayId")
            scheduleRebuild("changed:$displayId")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        rebuildOverlays(currentSettings)
        collectJob = scope.launch {
            val prefs = (application as ActivateApp).preferences
            prefs.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                rebuildOverlays(settings)
                if (!settings.overlayRunning) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    (application as ActivateApp).preferences.setOverlayRunning(false)
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        rebuildOverlays(currentSettings)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleRebuild("configChanged")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(reattachRunnable)
        displayManager?.unregisterDisplayListener(displayListener)
        displayManager = null
        collectJob?.cancel()
        scope.cancel()
        removeAllOverlays()
        super.onDestroy()
    }

    private fun scheduleRebuild(reason: String) {
        Log.d(TAG, "scheduleRebuild reason=$reason")
        mainHandler.removeCallbacks(reattachRunnable)
        // Coalesce rapid display events; short delay lets DeX finish creating the desktop display
        mainHandler.postDelayed(reattachRunnable, 150L)
    }

    private fun startAsForeground() {
        val openSettings = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WatermarkOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, ActivateApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.stop_overlay), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Desired targets: primary (PHONE/EXPANDED layout) + every usable external/desktop
     * display (DEX layout). When only DEFAULT_DISPLAY exists, only primary is shown.
     */
    private fun collectTargetDisplays(dm: DisplayManager): List<DisplayTarget> {
        val targets = ArrayList<DisplayTarget>()

        // Primary / DEFAULT_DISPLAY
        val primaryDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
        if (primaryDisplay != null && isDisplayUsable(primaryDisplay)) {
            val mode = primaryLayoutMode(primaryDisplay)
            targets.add(DisplayTarget(primaryDisplay, mode, isPrimary = true))
        }

        // External / desktop (Samsung DESKTOP, PRESENTATION, other non-default STATE_ON)
        val desktopIds = mutableSetOf<Int>()
        try {
            for (d in dm.getDisplays(SAMSUNG_DESKTOP_CATEGORY)) {
                desktopIds.add(d.displayId)
            }
        } catch (_: Throwable) {
            // category may be absent on non-Samsung
        }

        for (display in dm.displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY) continue
            if (!isDisplayUsable(display)) continue
            val score = scoreExternalDisplay(display, desktopIds.contains(display.displayId))
            if (score < MIN_EXTERNAL_SCORE) continue
            targets.add(DisplayTarget(display, LayoutMode.DEX, isPrimary = false))
        }

        return targets
    }

    private fun isDisplayUsable(display: Display): Boolean {
        return when (display.state) {
            Display.STATE_OFF,
            Display.STATE_DOZE,
            Display.STATE_DOZE_SUSPEND,
            Display.STATE_ON_SUSPEND -> false
            else -> true // STATE_ON, STATE_UNKNOWN (some DeX virtual displays)
        }
    }

    /**
     * Layout mode for the phone panel: PHONE or EXPANDED from that display's
     * configuration — never DEX just because DeX/desktop is also active.
     */
    private fun primaryLayoutMode(display: Display): LayoutMode {
        return try {
            val displayContext = createDisplayContext(display)
            LayoutModeDetector.detectPrimaryPanel(displayContext)
        } catch (t: Throwable) {
            Log.w(TAG, "primaryLayoutMode failed; falling back to PHONE", t)
            LayoutMode.PHONE
        }
    }

    private fun scoreExternalDisplay(display: Display, isSamsungDesktop: Boolean): Int {
        var score = 0
        if (isSamsungDesktop) score += 1000
        when (display.state) {
            Display.STATE_ON -> score += 200
            Display.STATE_UNKNOWN -> score += 80
            else -> score -= 50
        }
        if ((display.flags and Display.FLAG_PRESENTATION) != 0) {
            score += 100
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val megapixels = (metrics.widthPixels.toLong() * metrics.heightPixels) / 1_000_000L
        score += megapixels.toInt().coerceAtMost(50)
        return score
    }

    private fun rebuildOverlays(settings: WatermarkSettings) {
        val dm = displayManager ?: getSystemService(DisplayManager::class.java)
        if (dm == null) {
            Log.e(TAG, "DisplayManager unavailable")
            return
        }

        val targets = collectTargetDisplays(dm)
        val desiredIds = targets.map { it.display.displayId }.toSet()

        // Drop overlays for displays that are gone or no longer targeted
        val toRemove = overlays.keys.filter { it !in desiredIds }
        for (id in toRemove) {
            removeOverlay(id)
        }

        for (target in targets) {
            ensureOverlay(target, settings)
        }

        Log.d(
            TAG,
            "rebuild done overlays=${overlays.keys} targets=${targets.map { "${it.display.displayId}:${it.mode}" }}"
        )
    }

    private fun ensureOverlay(target: DisplayTarget, settings: WatermarkSettings) {
        val displayId = target.display.displayId
        val modeLayout: ModeLayoutSettings = settings.forMode(target.mode)

        var entry = overlays[displayId]
        if (entry == null) {
            entry = attachOverlay(target) ?: return
            overlays[displayId] = entry
        }

        applyToEntry(entry, settings, modeLayout)
    }

    private fun attachOverlay(target: DisplayTarget): OverlayEntry? {
        val display = target.display
        val displayId = display.displayId
        val displayContext = try {
            createDisplayContext(display)
        } catch (t: Throwable) {
            Log.e(TAG, "createDisplayContext failed displayId=$displayId", t)
            return null
        }
        val wm = displayContext.getSystemService(WindowManager::class.java)
        if (wm == null) {
            Log.e(TAG, "WindowManager null for displayId=$displayId")
            return null
        }

        // Use display context for the view so metrics/sp match that display
        val view = WatermarkView(displayContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 0
        }

        return try {
            wm.addView(view, params)
            Log.d(
                TAG,
                "Overlay attached displayId=$displayId mode=${target.mode} primary=${target.isPrimary}"
            )
            OverlayEntry(
                displayId = displayId,
                windowManager = wm,
                view = view,
                layoutParams = params,
                mode = target.mode,
                displayContext = displayContext,
            )
        } catch (t: Throwable) {
            // OEM quirk: keep other overlays; do not claim dual is impossible
            Log.e(TAG, "addView failed displayId=$displayId mode=${target.mode}; keeping others", t)
            null
        }
    }

    private fun applyToEntry(
        entry: OverlayEntry,
        settings: WatermarkSettings,
        modeLayout: ModeLayoutSettings,
    ) {
        entry.view.applySettings(settings, modeLayout)

        val density = entry.displayContext.resources.displayMetrics
        entry.layoutParams.gravity = gravityFor(modeLayout.anchor)
        entry.layoutParams.x = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            modeLayout.offsetXDp,
            density
        ).toInt()
        entry.layoutParams.y = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            modeLayout.offsetYDp,
            density
        ).toInt()

        try {
            entry.windowManager.updateViewLayout(entry.view, entry.layoutParams)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout failed displayId=${entry.displayId}; reattach", t)
            removeOverlay(entry.displayId)
            val dm = displayManager ?: return
            val display = dm.getDisplay(entry.displayId) ?: return
            val target = DisplayTarget(display, entry.mode, isPrimary = entry.displayId == Display.DEFAULT_DISPLAY)
            val fresh = attachOverlay(target) ?: return
            overlays[fresh.displayId] = fresh
            applyToEntry(fresh, settings, modeLayout)
        }
    }

    private fun removeOverlay(displayId: Int) {
        val entry = overlays.remove(displayId) ?: return
        try {
            entry.windowManager.removeView(entry.view)
        } catch (_: Throwable) {
            // already removed
        }
        Log.d(TAG, "Overlay removed displayId=$displayId")
    }

    private fun removeAllOverlays() {
        val ids = overlays.keys.toList()
        for (id in ids) {
            removeOverlay(id)
        }
    }

    private fun gravityFor(anchor: WatermarkSettings.Anchor): Int = when (anchor) {
        WatermarkSettings.Anchor.TOP_START -> Gravity.TOP or Gravity.START
        WatermarkSettings.Anchor.TOP_END -> Gravity.TOP or Gravity.END
        WatermarkSettings.Anchor.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
        WatermarkSettings.Anchor.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
        WatermarkSettings.Anchor.CENTER -> Gravity.CENTER
    }

    private data class DisplayTarget(
        val display: Display,
        val mode: LayoutMode,
        val isPrimary: Boolean,
    )

    private data class OverlayEntry(
        val displayId: Int,
        val windowManager: WindowManager,
        val view: WatermarkView,
        val layoutParams: WindowManager.LayoutParams,
        val mode: LayoutMode,
        val displayContext: Context,
    )

    companion object {
        private const val TAG = "WatermarkOverlay"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "app.nekonya.activateandroid.STOP_OVERLAY"
        /** Samsung DisplayManager category for DeX / Dual Mode desktop display. */
        const val SAMSUNG_DESKTOP_CATEGORY =
            "com.samsung.android.hardware.display.category.DESKTOP"
        /** Minimum score for a non-default display to receive an overlay. */
        private const val MIN_EXTERNAL_SCORE = 50

        fun start(context: Context) {
            val intent = Intent(context, WatermarkOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WatermarkOverlayService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
