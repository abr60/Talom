package com.talom.source.whatsapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.talom.R

object TalomNotification {
    private const val CHANNEL_ID = "talom_pull_updates"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Talom updates",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "New items found by scheduled Talom pulls"
            },
        )
    }

    fun notifyPullResults(context: Context, academicCount: Int, insightCount: Int) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val parts = buildList {
            if (academicCount > 0) add("$academicCount academic")
            if (insightCount > 0) add("$insightCount personal/general")
        }
        if (parts.isEmpty()) return
        NotificationManagerCompat.from(context).notify(
            1001,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Talom found new information")
                .setContentText(parts.joinToString(", "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString(", ")))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build(),
        )
    }
}
