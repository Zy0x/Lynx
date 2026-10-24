package com.noir.lynx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.ui.MainActivity
import java.util.Locale

/**
 * Helper class for Lynx App Automation notifications and battery telemetry.
 * Isolated to keep service logic cleanly separated and ultra-lightweight.
 */
class LynxAutomationNotificationHelper(private val context: Context) {

    companion object {
        const val NOTIF_CHANNEL_ID = "lynx_app_automation_channel"
        const val NOTIF_ID = 8802
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastNotifTitle = ""
    private var lastNotifStatus = ""
    private var lastNotifProfile = ""

    @Volatile var cachedBatteryPct = 0
    @Volatile var cachedBatteryTemp = 0f

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                AppLocale.getString(context, R.string.automation_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = AppLocale.getString(context, R.string.automation_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(titleText: String, statusText: String, profile: String): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconRes = getModeIconRes(profile)
        val colorInt = getModeColor(profile)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NOTIF_CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(statusText)
                .setSmallIcon(iconRes)
                .setColor(colorInt)
                .setColorized(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pOpenIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setContentTitle(titleText)
                .setContentText(statusText)
                .setSmallIcon(iconRes)
                .setColor(colorInt)
                .setOnlyAlertOnce(true)
                .setContentIntent(pOpenIntent)
                .build()
        }
    }

    fun updateNotification(titleText: String, statusText: String, profile: String) {
        if (titleText == lastNotifTitle && statusText == lastNotifStatus && profile == lastNotifProfile) {
            return
        }
        lastNotifTitle = titleText
        lastNotifStatus = statusText
        lastNotifProfile = profile
        notificationManager.notify(NOTIF_ID, buildNotification(titleText, statusText, profile))
    }

    fun getProfileDisplayName(profile: String): String {
        val profileNameRes = when (profile.lowercase().trim()) {
            "extreme" -> R.string.profile_name_extreme
            "performance" -> R.string.profile_name_performance
            "powersave" -> R.string.profile_name_powersave
            "auto" -> R.string.profile_name_auto
            else -> R.string.profile_name_balance
        }
        return AppLocale.getString(context, profileNameRes)
    }

    fun getStandbySubtitle(base: String, pct: Int, tempC: Float): String {
        val tele = formatTelemetry(pct, tempC)
        val baseName = getProfileDisplayName(base)
        return "Otomasi Siaga • Profil Dasar: $baseName$tele"
    }

    fun formatModeTitle(profile: String): String {
        val profileName = getProfileDisplayName(profile)
        return AppLocale.getString(
            context,
            R.string.automation_notification_title,
            profileName
        )
    }

    fun getModeIconRes(profile: String): Int {
        return when (profile.lowercase().trim()) {
            "extreme" -> R.drawable.ic_mode_extreme
            "performance" -> R.drawable.ic_mode_performance
            "powersave" -> R.drawable.ic_mode_powersave
            "auto" -> R.drawable.ic_mode_auto
            else -> R.drawable.ic_mode_balance
        }
    }

    fun getModeColor(profile: String): Int {
        return when (profile.lowercase().trim()) {
            "extreme" -> 0xFFFF3B30.toInt()      // Flame Red
            "performance" -> 0xFFFF9500.toInt()  // Amber Lightning
            "powersave" -> 0xFF34C759.toInt()    // Eco Green
            "auto" -> 0xFFAF52DE.toInt()         // Neon Purple
            else -> 0xFF007AFF.toInt()           // Cyber Blue
        }
    }

    fun formatTelemetry(pct: Int, tempC: Float): String {
        return if (pct > 0 && tempC > 0f) {
            String.format(Locale.US, " • %d°C • %d%%", kotlin.math.round(tempC).toInt(), pct)
        } else if (pct > 0) {
            " • $pct%"
        } else ""
    }

    fun getLiveBatteryTelemetry(): Pair<Int, Float> {
        if (cachedBatteryPct > 0 || cachedBatteryTemp > 0f) {
            return Pair(cachedBatteryPct, cachedBatteryTemp)
        }
        return try {
            val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            val rawTemp = status?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = if (rawTemp > 0) rawTemp / 10.0f else 0f
            if (pct > 0) cachedBatteryPct = pct
            if (tempC > 0f) cachedBatteryTemp = tempC
            Pair(pct, tempC)
        } catch (_: Exception) {
            Pair(0, 0f)
        }
    }
}
