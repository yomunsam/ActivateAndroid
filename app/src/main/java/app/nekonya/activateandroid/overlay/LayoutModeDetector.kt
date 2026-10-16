package app.nekonya.activateandroid.overlay

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.view.InputDevice

/**
 * Detects phone / expanded / DeX layout mode for per-mode watermark anchors.
 *
 * Detection order (device-wide [detect]):
 * 1. Samsung DeX via Settings.System `sem_desktop_mode_enabled` or reflection on
 *    Configuration.semDesktopModeEnabled (OEM field).
 * 2. [Configuration.UI_MODE_TYPE_DESKTOP] when present (API 35+/platform desktop).
 * 3. Heuristic: smallestScreenWidthDp >= 600 → EXPANDED, else PHONE.
 *    Optional: keyboard/mouse attached bumps toward DEX-like desktop when sw large.
 *
 * For dual-overlay on the **phone panel** while DeX Dual Mode is active, use
 * [detectPrimaryPanel] which never returns DEX (PHONE or EXPANDED only).
 */
object LayoutModeDetector {
    private const val TAG = "LayoutModeDetector"
    private const val SEM_DESKTOP_SETTING = "sem_desktop_mode_enabled"

    fun detect(context: Context): LayoutMode {
        val config = context.resources.configuration

        if (isSamsungDex(context, config)) {
            Log.d(TAG, "mode=DEX (Samsung DeX)")
            return LayoutMode.DEX
        }

        if (isDesktopUiMode(config)) {
            Log.d(TAG, "mode=DEX (UI_MODE_TYPE_DESKTOP)")
            return LayoutMode.DEX
        }

        val sw = config.smallestScreenWidthDp
        if (sw >= 600) {
            // Large screen with pointer/keyboard often feels like DeX/desktop
            if (hasDesktopPeripherals() && sw >= 720) {
                Log.d(TAG, "mode=DEX (sw=$sw + peripherals)")
                return LayoutMode.DEX
            }
            Log.d(TAG, "mode=EXPANDED (sw=$sw)")
            return LayoutMode.EXPANDED
        }

        Log.d(TAG, "mode=PHONE (sw=$sw)")
        return LayoutMode.PHONE
    }

    /**
     * Layout for the primary / phone panel only. Ignores DeX / desktop UI mode
     * so Dual Mode can show PHONE or EXPANDED on the handset while DEX layout
     * is applied separately on the external desktop display.
     */
    fun detectPrimaryPanel(context: Context): LayoutMode {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw >= 600) {
            Log.d(TAG, "primaryPanel=EXPANDED (sw=$sw)")
            LayoutMode.EXPANDED
        } else {
            Log.d(TAG, "primaryPanel=PHONE (sw=$sw)")
            LayoutMode.PHONE
        }
    }

    private fun isSamsungDex(context: Context, config: Configuration): Boolean {
        try {
            val enabled = Settings.System.getInt(
                context.contentResolver,
                SEM_DESKTOP_SETTING,
                0
            )
            if (enabled == 1) return true
        } catch (_: Throwable) {
            // ignore
        }

        // Reflection: Configuration.semDesktopModeEnabled (Samsung)
        try {
            val field = Configuration::class.java.getField("semDesktopModeEnabled")
            val value = field.getInt(config)
            val enabledConst = Configuration::class.java
                .getField("SEM_DESKTOP_MODE_ENABLED")
                .getInt(null)
            if (value == enabledConst) return true
        } catch (_: Throwable) {
            // Not a Samsung build / field absent
        }
        return false
    }

    private fun isDesktopUiMode(config: Configuration): Boolean {
        // UI_MODE_TYPE_DESKTOP = 0x0f on newer platforms; use constant when available
        val uiType = config.uiMode and Configuration.UI_MODE_TYPE_MASK
        val desktopType = try {
            Configuration::class.java.getField("UI_MODE_TYPE_DESKTOP").getInt(null)
        } catch (_: Throwable) {
            0x0f // documented value when field exists
        }
        return uiType == desktopType
    }

    private fun hasDesktopPeripherals(): Boolean {
        return try {
            val devices = InputDevice.getDeviceIds()
            var hasMouse = false
            var hasKeyboard = false
            for (id in devices) {
                val device = InputDevice.getDevice(id) ?: continue
                val sources = device.sources
                if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
                    hasMouse = true
                }
                if (
                    sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                ) {
                    hasKeyboard = true
                }
            }
            hasMouse || hasKeyboard
        } catch (_: Throwable) {
            false
        }
    }
}
