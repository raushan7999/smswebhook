package com.bestcallerapp.smswebhook.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.bestcallerapp.smswebhook.R

/**
 * Expedited work runs as a short foreground service on Android 11 and lower, which requires a
 * notification. On Android 12+ expedited jobs need no notification and this is never shown.
 */
object WorkerNotifications {
    const val CHANNEL_ID = "background_delivery"
    private const val NOTIFICATION_ID = 4_201

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_delivery),
            NotificationManager.IMPORTANCE_MIN,
        ).apply { description = context.getString(R.string.notification_channel_delivery_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun foregroundInfo(context: Context): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forward)
            .setContentTitle(context.getString(R.string.notification_forwarding_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
