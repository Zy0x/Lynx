package com.noir.lynx.feature.hud.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale

internal fun hudForegroundNotification(context: Context, channel: String, stopIntent: Intent): Notification {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(channel, AppLocale.getString(context, R.string.floating_hud_channel_name), NotificationManager.IMPORTANCE_LOW))
    val launch = Intent(context, com.noir.lynx.ui.MainActivity::class.java)
    val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val stop = PendingIntent.getService(context, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return Notification.Builder(context, channel).setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(AppLocale.getString(context, R.string.floating_hud_notification_title))
        .setContentText(AppLocale.getString(context, R.string.floating_hud_notification_tap_close))
        .setContentIntent(pending).setOngoing(true)
        .addAction(Notification.Action.Builder(null, AppLocale.getString(context, R.string.floating_hud_action_close), stop).build()).build()
}
