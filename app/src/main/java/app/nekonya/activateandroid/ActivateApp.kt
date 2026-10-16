package app.nekonya.activateandroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import app.nekonya.activateandroid.data.WatermarkPreferences

class ActivateApp : Application() {
    lateinit var preferences: WatermarkPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = WatermarkPreferences(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "watermark_overlay"
    }
}
