package com.example.data.repository

import android.content.Context
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog
import com.example.data.model.SalonPreference
import com.google.firebase.Timestamp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable on-device booking & preference repository scoped to a verified phone session
 * (`phone_<digits>`), allowing customers who register via Phone + OTP (without Google Sign-In)
 * to create, view, cancel, and rate bookings seamlessly.
 */
class PhoneLocalBookingRepository(
    context: Context,
    private val phoneSessionId: String,
) : SalonBookingDataSource {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val sanitizedKey = phoneSessionId.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "default_phone" }
    private val bookingsFile = File(appContext.filesDir, "bookings_$sanitizedKey.json")
    private val prefFile = File(appContext.filesDir, "salon_pref_$sanitizedKey.json")

    private val _appointmentsFlow = MutableStateFlow<List<Appointment>>(readAppointmentsFromDisk())
    private val _preferenceFlow = MutableStateFlow<SalonPreference?>(readPreferenceFromDisk())

    override fun observeAppointments(userId: String): Flow<List<Appointment>> = _appointmentsFlow.asStateFlow()

    override fun observeAllSalonBookingsForOwner(): Flow<List<Appointment>> = _appointmentsFlow.asStateFlow()

    override suspend fun getAppointmentById(appointmentId: String, userId: String): Appointment? {
        return _appointmentsFlow.value.find { it.id == appointmentId }
    }

    override suspend fun createAppointment(appointment: Appointment): Appointment = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val newAppointment = appointment.copy(
                id = UUID.randomUUID().toString().replace("-", "").take(20),
                userId = phoneSessionId,
                customerName = appointment.customerName.trim(),
                customerPhone = appointment.customerPhone.trim(),
                packageTitle = appointment.packageTitle.take(140),
                packageServices = appointment.packageServices.take(550),
                selectedHaircut = appointment.selectedHaircut.take(180),
                notes = appointment.notes.trim().take(300),
                status = "confirmed",
                rating = 0L,
                feedbackComment = "",
                createdAt = now,
                updatedAt = now,
            )
            val updatedList = (_appointmentsFlow.value + newAppointment)
                .sortedByDescending { it.appointmentEpochMillis }
            writeAppointmentsToDisk(updatedList)
            _appointmentsFlow.value = updatedList
            newAppointment
        }
    }

    override suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: String,
        reminderScheduled: Boolean,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val updatedList = _appointmentsFlow.value.map { existing ->
                if (existing.id == appointmentId) {
                    existing.copy(
                        status = newStatus,
                        reminderScheduled = reminderScheduled,
                        updatedAt = now,
                    )
                } else {
                    existing
                }
            }
            writeAppointmentsToDisk(updatedList)
            _appointmentsFlow.value = updatedList
        }
    }

    override suspend fun updateBookingStatusByOwner(
        appointment: Appointment,
        newStatus: String,
    ) {
        updateAppointmentStatus(appointment.id, newStatus, reminderScheduled = false)
    }

    override suspend fun submitAppointmentFeedback(
        appointmentId: String,
        rating: Int,
        feedbackComment: String,
    ) = withContext(Dispatchers.IO) {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        mutex.withLock {
            val now = Timestamp.now()
            val updatedList = _appointmentsFlow.value.map { existing ->
                if (existing.id == appointmentId && existing.status == "completed") {
                    existing.copy(
                        rating = rating.toLong(),
                        feedbackComment = feedbackComment.trim().take(500),
                        updatedAt = now,
                    )
                } else {
                    existing
                }
            }
            writeAppointmentsToDisk(updatedList)
            _appointmentsFlow.value = updatedList
        }
    }

    override suspend fun deleteAppointment(appointmentId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updatedList = _appointmentsFlow.value.filterNot { it.id == appointmentId }
            writeAppointmentsToDisk(updatedList)
            _appointmentsFlow.value = updatedList
        }
    }

    override fun observeSalonPreference(userId: String): Flow<SalonPreference?> = _preferenceFlow.asStateFlow()

    override suspend fun saveSalonPreference(
        savedCustomerName: String,
        savedPhone: String,
        barber2CustomName: String,
        barber3CustomName: String,
        salonWhatsappPhone: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val current = _preferenceFlow.value
            val updated = SalonPreference(
                id = "profile",
                userId = phoneSessionId,
                savedCustomerName = savedCustomerName.trim().take(80),
                savedPhone = savedPhone.trim().take(20),
                barber2CustomName = barber2CustomName.trim().take(50),
                barber3CustomName = barber3CustomName.trim().take(50),
                salonWhatsappPhone = salonWhatsappPhone.trim().ifEmpty { SalonCatalog.DEFAULT_SALON_PHONE }.take(20),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            writePreferenceToDisk(updated)
            _preferenceFlow.value = updated
        }
    }

    private fun readAppointmentsFromDisk(): List<Appointment> {
        if (!bookingsFile.exists()) return emptyList()
        return runCatching {
            val raw = bookingsFile.readText()
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        Appointment(
                            id = obj.optString("id"),
                            userId = obj.optString("userId", phoneSessionId),
                            customerName = obj.optString("customerName"),
                            customerPhone = obj.optString("customerPhone"),
                            barberId = obj.optString("barberId", "mody"),
                            barberName = obj.optString("barberName", "مودي"),
                            packageId = obj.optString("packageId"),
                            packageTitle = obj.optString("packageTitle"),
                            packageServices = obj.optString("packageServices"),
                            selectedHaircut = obj.optString("selectedHaircut"),
                            appointmentDate = obj.optString("appointmentDate"),
                            appointmentTime = obj.optString("appointmentTime"),
                            appointmentEpochMillis = obj.optLong("appointmentEpochMillis", 0L),
                            notes = obj.optString("notes"),
                            status = obj.optString("status", "confirmed"),
                            reminderScheduled = obj.optBoolean("reminderScheduled", true),
                            rating = obj.optLong("rating", 0L),
                            feedbackComment = obj.optString("feedbackComment", ""),
                        )
                    )
                }
            }.sortedByDescending { it.appointmentEpochMillis }
        }.getOrDefault(emptyList())
    }

    private fun writeAppointmentsToDisk(list: List<Appointment>) {
        val arr = JSONArray()
        list.forEach { appt ->
            arr.put(
                JSONObject().apply {
                    put("id", appt.id)
                    put("userId", appt.userId)
                    put("customerName", appt.customerName)
                    put("customerPhone", appt.customerPhone)
                    put("barberId", appt.barberId)
                    put("barberName", appt.barberName)
                    put("packageId", appt.packageId)
                    put("packageTitle", appt.packageTitle)
                    put("packageServices", appt.packageServices)
                    put("selectedHaircut", appt.selectedHaircut)
                    put("appointmentDate", appt.appointmentDate)
                    put("appointmentTime", appt.appointmentTime)
                    put("appointmentEpochMillis", appt.appointmentEpochMillis)
                    put("notes", appt.notes)
                    put("status", appt.status)
                    put("reminderScheduled", appt.reminderScheduled)
                    put("rating", appt.rating)
                    put("feedbackComment", appt.feedbackComment)
                }
            )
        }
        bookingsFile.writeText(arr.toString())
    }

    private fun readPreferenceFromDisk(): SalonPreference? {
        if (!prefFile.exists()) return null
        return runCatching {
            val obj = JSONObject(prefFile.readText())
            SalonPreference(
                id = "profile",
                userId = obj.optString("userId", phoneSessionId),
                savedCustomerName = obj.optString("savedCustomerName"),
                savedPhone = obj.optString("savedPhone"),
                barber2CustomName = obj.optString("barber2CustomName"),
                barber3CustomName = obj.optString("barber3CustomName"),
                salonWhatsappPhone = obj.optString("salonWhatsappPhone", SalonCatalog.DEFAULT_SALON_PHONE),
            )
        }.getOrNull()
    }

    private fun writePreferenceToDisk(pref: SalonPreference) {
        val obj = JSONObject().apply {
            put("userId", pref.userId)
            put("savedCustomerName", pref.savedCustomerName)
            put("savedPhone", pref.savedPhone)
            put("barber2CustomName", pref.barber2CustomName)
            put("barber3CustomName", pref.barber3CustomName)
            put("salonWhatsappPhone", pref.salonWhatsappPhone)
        }
        prefFile.writeText(obj.toString())
    }
}
