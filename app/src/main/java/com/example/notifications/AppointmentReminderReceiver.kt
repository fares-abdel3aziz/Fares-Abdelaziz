package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class AppointmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME) ?: "عميلنا العزيز"
        val barberName = intent.getStringExtra(EXTRA_BARBER_NAME) ?: "مودي"
        val packageTitle = intent.getStringExtra(EXTRA_PACKAGE_TITLE) ?: "باكيدج أبو العطا"
        val appointmentTime = intent.getStringExtra(EXTRA_APPOINTMENT_TIME) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2001)

        showReminderNotification(
            context = context,
            notificationId = notificationId,
            title = context.getString(R.string.notif_reminder_title),
            body = "يا $customerName، باقي نصف ساعة على ميعادك الساعة $appointmentTime مع $barberName ($packageTitle). في انتظارك في صالون أبو العطا!",
        )
    }

    companion object {
        const val CHANNEL_ID = "abo_al_ata_appointments"
        const val EXTRA_CUSTOMER_NAME = "extra_customer_name"
        const val EXTRA_BARBER_NAME = "extra_barber_name"
        const val EXTRA_PACKAGE_TITLE = "extra_package_title"
        const val EXTRA_APPOINTMENT_TIME = "extra_appointment_time"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun showReminderNotification(
            context: Context,
            notificationId: Int,
            title: String,
            body: String,
        ) {
            ensureChannel(context)
            if (!hasNotificationPermission(context)) return

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } catch (_: SecurityException) {
                // Handled gracefully if notifications are revoked by user
            }
        }
    }
}
