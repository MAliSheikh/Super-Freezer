package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "subzero_freezer_channel"
    private const val CHANNEL_NAME = "Subzero App Freezer Actions"

    fun showFreezeNotification(context: Context, appName: String, packageName: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when background apps are successfully frozen."
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Using standard Android drawable to guarantee compatibility and eliminate compile faults
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("❄️ Subzero: App Frozen")
                .setContentText("$appName ($packageName) is successfully frozen.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            notificationManager.notify(packageName.hashCode(), builder.build())
        } catch (e: Exception) {
            // safe fallback for notification permissions or background restrictions
        }
    }

    fun showAlreadyFrozenNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when background apps are successfully frozen."
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("❄️ Subzero: Already Frozen")
                .setContentText("All selected apps are already dormant & frozen.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            notificationManager.notify(9999, builder.build())
        } catch (e: Exception) {
            // safe fallback
        }
    }
}
