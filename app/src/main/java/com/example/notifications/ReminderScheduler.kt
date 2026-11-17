package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.R
import com.example.data.model.Appointment
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val THIRTY_MINUTES_MS = 30L * 60L * 1000L
    const val WORK_TAG_REMINDERS = "abo_al_ata_appointment_reminders"
    private const val NEAR_TERM_FALLBACK_DELAY_MS = 5_000L

    fun uniqueWorkName(appointmentId: String): String = "appointment_reminder_$appointmentId"

    /**
     * Computes the initial delay in milliseconds so the WorkManager reminder fires 30 minutes
     * before [appointmentEpochMillis]. Returns `null` if the appointment time has already passed.
     */
    fun computeThirtyMinuteReminderDelayMillis(
        appointmentEpochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val triggerAtMillis = appointmentEpochMillis - THIRTY_MINUTES_MS
        return when {
            triggerAtMillis > nowMillis -> triggerAtMillis - nowMillis
            appointmentEpochMillis > nowMillis -> NEAR_TERM_FALLBACK_DELAY_MS
            else -> null
        }
    }

    fun scheduleAppointmentNotifications(
        context: Context,
        appointment: Appointment,
        postImmediateConfirmation: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val appContext = context.applicationContext
        AppointmentReminderReceiver.ensureChannel(appContext)

        val stableKey = appointment.id.ifBlank { "${appointment.customerPhone}_${appointment.appointmentEpochMillis}" }
        val notifId = (stableKey.hashCode() and 0x7FFFFFFF)

        // 1. Immediate booking confirmation notification from the app
        if (postImmediateConfirmation) {
            val haircutPart = if (appointment.selectedHaircut.isNotBlank()) {
                " • القصة: ${appointment.selectedHaircut}"
            } else {
                ""
            }
            AppointmentReminderReceiver.showReminderNotification(
                context = appContext,
                notificationId = notifId xor 0x5A5A,
                title = appContext.getString(R.string.notif_confirmed_title),
                body = "تم تأكيد حجز ${appointment.customerName} (${appointment.customerPhone}) يوم ${appointment.appointmentDate} الساعة ${appointment.appointmentTime} مع ${appointment.barberName} — ${appointment.packageTitle}$haircutPart. سيصلك إشعار قبل الميعاد بـ 30 دقيقة.",
            )
        }

        // 2. Schedule 30-minute prior local notification via WorkManager
        if (!appointment.reminderScheduled) {
            if (appointment.id.isNotBlank()) {
                cancelReminder(appContext, appointment.id)
            }
            return false
        }

        val initialDelayMs = computeThirtyMinuteReminderDelayMillis(
            appointmentEpochMillis = appointment.appointmentEpochMillis,
            nowMillis = nowMillis,
        ) ?: return false

        val inputData = workDataOf(
            AppointmentReminderWorker.KEY_APPOINTMENT_ID to stableKey,
            AppointmentReminderWorker.KEY_CUSTOMER_NAME to appointment.customerName,
            AppointmentReminderWorker.KEY_BARBER_NAME to appointment.barberName,
            AppointmentReminderWorker.KEY_PACKAGE_TITLE to appointment.packageTitle,
            AppointmentReminderWorker.KEY_APPOINTMENT_DATE to appointment.appointmentDate,
            AppointmentReminderWorker.KEY_APPOINTMENT_TIME to appointment.appointmentTime,
            AppointmentReminderWorker.KEY_NOTIFICATION_ID to notifId,
        )

        val reminderWorkRequest = OneTimeWorkRequestBuilder<AppointmentReminderWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(WORK_TAG_REMINDERS)
            .build()

        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                uniqueWorkName(stableKey),
                ExistingWorkPolicy.REPLACE,
                reminderWorkRequest,
            )
        }

        // Secondary AlarmManager wake backup for exact Doze-mode wakeup
        runCatching {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val pendingIntent = buildPendingIntent(appContext, appointment, notifId)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nowMillis + initialDelayMs,
                    pendingIntent,
                )
            }
        }

        return true
    }

    fun triggerInstantPreviewReminder(context: Context, appointment: Appointment): Boolean {
        val appContext = context.applicationContext
        if (!AppointmentReminderReceiver.hasNotificationPermission(appContext)) {
            return false
        }
        AppointmentReminderReceiver.showReminderNotification(
            context = appContext,
            notificationId = (appointment.id.hashCode() and 0x7FFFFFFF),
            title = appContext.getString(R.string.notif_reminder_title),
            body = "يا ${appointment.customerName}، باقي 30 دقيقة على موعدك الساعة ${appointment.appointmentTime} مع ${appointment.barberName} (${appointment.packageTitle}). في انتظارك في صالون أبو العطا!",
        )
        return true
    }

    fun cancelReminder(context: Context, appointmentId: String) {
        if (appointmentId.isBlank()) return
        val appContext = context.applicationContext

        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(appointmentId))
        }

        runCatching {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(appContext, AppointmentReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                (appointmentId.hashCode() and 0x7FFFFFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun buildPendingIntent(
        context: Context,
        appointment: Appointment,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra(AppointmentReminderReceiver.EXTRA_CUSTOMER_NAME, appointment.customerName)
            putExtra(AppointmentReminderReceiver.EXTRA_BARBER_NAME, appointment.barberName)
            putExtra(AppointmentReminderReceiver.EXTRA_PACKAGE_TITLE, appointment.packageTitle)
            putExtra(AppointmentReminderReceiver.EXTRA_APPOINTMENT_TIME, appointment.appointmentTime)
            putExtra(AppointmentReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
