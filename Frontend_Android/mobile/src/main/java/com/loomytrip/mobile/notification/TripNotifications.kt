package com.loomytrip.mobile.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.loomytrip.mobile.MainActivity

const val TRIP_NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

fun canShowTripNotification(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, TRIP_NOTIFICATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

fun showTripReadyNotification(context: Context, placeCount: Int) {
    if (!canShowTripNotification(context)) return

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            "trip_updates",
            "Trip updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )
    )

    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, "trip_updates")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Your itinerary is ready")
        .setContentText("$placeCount places were added to your draft itinerary.")
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(1001, notification)
}
