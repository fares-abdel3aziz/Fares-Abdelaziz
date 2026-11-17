package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R

/**
 * Background WorkManager worker scheduled 30 minutes prior to a confirmed salon appointment
 * to post a high-priority local reminder notification to the customer.
 */
class AppointmentReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val customerName = inputData.getString(KEY_CUSTOMER_NAME)?.ifBlank { null } ?: "عميلنا العزيز"
        val barberName = inputData.getString(KEY_BARBER_NAME)?.ifBlank { null } ?: "مودي"
        val packageTitle = inputData.getString(KEY_PACKAGE_TITLE)?.ifBlank { null } ?: "باكيدج أبو العطا"
        val appointmentTime = inputData.getString(KEY_APPOINTMENT_TIME).orEmpty()
        val appointmentDate = inputData.getString(KEY_APPOINTMENT_DATE).orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)

        val dateSegment = if (appointmentDate.isNotBlank()) " ($appointmentDate)" else ""
        val body = "يا $customerName، باقي 30 دقيقة على موعدك الساعة $appointmentTime$dateSegment مع $barberName ($packageTitle). في انتظارك في صالون أبو العطا!"

        AppointmentReminderReceiver.showReminderNotification(
            context = applicationContext,
            notificationId = notificationId,
            title = applicationContext.getString(R.string.notif_reminder_title),
            body = body,
        )

        return Result.success()
    }

    companion object {
        const val KEY_APPOINTMENT_ID = "key_appointment_id"
        const val KEY_CUSTOMER_NAME = "key_customer_name"
        const val KEY_BARBER_NAME = "key_barber_name"
        const val KEY_PACKAGE_TITLE = "key_package_title"
        const val KEY_APPOINTMENT_DATE = "key_appointment_date"
        const val KEY_APPOINTMENT_TIME = "key_appointment_time"
        const val KEY_NOTIFICATION_ID = "key_notification_id"
        private const val DEFAULT_NOTIFICATION_ID = 3030
    }
}
