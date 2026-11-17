package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog
import com.example.data.model.SalonPreference
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface SalonBookingDataSource {
    fun observeAppointments(userId: String): Flow<List<Appointment>>
    fun observeAllSalonBookingsForOwner(): Flow<List<Appointment>>
    suspend fun getAppointmentById(appointmentId: String, userId: String): Appointment?
    suspend fun createAppointment(appointment: Appointment): Appointment
    suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: String,
        reminderScheduled: Boolean = false,
    )
    suspend fun updateBookingStatusByOwner(
        appointment: Appointment,
        newStatus: String,
    )
    suspend fun submitAppointmentFeedback(
        appointmentId: String,
        rating: Int,
        feedbackComment: String,
    )
    suspend fun deleteAppointment(appointmentId: String)
    fun observeSalonPreference(userId: String): Flow<SalonPreference?>
    suspend fun saveSalonPreference(
        savedCustomerName: String,
        savedPhone: String,
        barber2CustomName: String,
        barber3CustomName: String,
        salonWhatsappPhone: String = SalonCatalog.DEFAULT_SALON_PHONE,
    )
}

class BookingRepository(private val db: FirebaseFirestore) : SalonBookingDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول برقم الهاتف أو حساب Google أولاً للوصول إلى الحجوزات.")
    }

    override fun observeAppointments(userId: String): Flow<List<Appointment>> = flow {
        val path = "users/$userId/appointments"
        emitAll(
            db.collection("users").document(userId).collection("appointments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Appointment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.appointmentEpochMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Streams all appointments in `/salon_bookings` exclusively for the verified Salon Owner.
     * Protected on the backend by `isSalonOwnerToken()` in `firestore.rules`.
     */
    override fun observeAllSalonBookingsForOwner(): Flow<List<Appointment>> = flow {
        val ownerUid = requireUserId()
        val path = "salon_bookings"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Appointment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.appointmentEpochMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun getAppointmentById(appointmentId: String, userId: String): Appointment? {
        val docRef = db.collection("users").document(userId)
            .collection("appointments").document(appointmentId)
        return try {
            val snapshot = docRef.get().await()
            snapshot.toObject(Appointment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    override suspend fun createAppointment(appointment: Appointment): Appointment {
        val uid = requireUserId()
        val userDocRef = db.collection("users").document(uid).collection("appointments").document()
        val salonDocRef = db.collection("salon_bookings").document(userDocRef.id)
        val payload = mapOf(
            "userId" to uid,
            "customerName" to appointment.customerName.trim(),
            "customerPhone" to appointment.customerPhone.trim(),
            "barberId" to appointment.barberId,
            "barberName" to appointment.barberName,
            "packageId" to appointment.packageId,
            "packageTitle" to appointment.packageTitle.take(140),
            "packageServices" to appointment.packageServices.take(550),
            "selectedHaircut" to appointment.selectedHaircut.take(180),
            "appointmentDate" to appointment.appointmentDate,
            "appointmentTime" to appointment.appointmentTime,
            "appointmentEpochMillis" to appointment.appointmentEpochMillis,
            "notes" to appointment.notes.trim().take(300),
            "status" to "confirmed",
            "reminderScheduled" to appointment.reminderScheduled,
            "rating" to 0L,
            "feedbackComment" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            val batch = db.batch()
            batch.set(userDocRef, payload)
            batch.set(salonDocRef, payload)
            batch.commit().await()

            val now = Timestamp.now()
            return appointment.copy(
                id = userDocRef.id,
                userId = uid,
                status = "confirmed",
                createdAt = now,
                updatedAt = now,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userDocRef.path)
            throw e
        }
    }

    override suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: String,
        reminderScheduled: Boolean,
    ) {
        val uid = requireUserId()
        require(appointmentId.isNotBlank()) { "Appointment ID must not be blank" }
        val userDocRef = db.collection("users").document(uid)
            .collection("appointments").document(appointmentId)
        val salonDocRef = db.collection("salon_bookings").document(appointmentId)
        val updates = mapOf(
            "status" to newStatus,
            "reminderScheduled" to reminderScheduled,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            userDocRef.update(updates).await()
            runCatching { salonDocRef.update(updates).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDocRef.path)
            throw e
        }
    }

    override suspend fun updateBookingStatusByOwner(
        appointment: Appointment,
        newStatus: String,
    ) {
        requireUserId()
        require(appointment.id.isNotBlank()) { "Appointment ID must not be blank" }
        val salonDocRef = db.collection("salon_bookings").document(appointment.id)
        val updates = mapOf(
            "status" to newStatus,
            "reminderScheduled" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            salonDocRef.update(updates).await()
            if (appointment.userId.isNotBlank()) {
                val customerDocRef = db.collection("users").document(appointment.userId)
                    .collection("appointments").document(appointment.id)
                runCatching { customerDocRef.update(updates).await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, salonDocRef.path)
            throw e
        }
    }

    override suspend fun submitAppointmentFeedback(
        appointmentId: String,
        rating: Int,
        feedbackComment: String,
    ) {
        val uid = requireUserId()
        require(appointmentId.isNotBlank()) { "Appointment ID must not be blank" }
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        val userDocRef = db.collection("users").document(uid)
            .collection("appointments").document(appointmentId)
        val salonDocRef = db.collection("salon_bookings").document(appointmentId)
        val updates = mapOf(
            "rating" to rating.toLong(),
            "feedbackComment" to feedbackComment.trim().take(500),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            userDocRef.update(updates).await()
            runCatching { salonDocRef.update(updates).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDocRef.path)
            throw e
        }
    }

    override suspend fun deleteAppointment(appointmentId: String) {
        val uid = requireUserId()
        require(appointmentId.isNotBlank()) { "Appointment ID must not be blank" }
        val userDocRef = db.collection("users").document(uid)
            .collection("appointments").document(appointmentId)
        val salonDocRef = db.collection("salon_bookings").document(appointmentId)
        try {
            userDocRef.delete().await()
            runCatching { salonDocRef.delete().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDocRef.path)
            throw e
        }
    }

    override fun observeSalonPreference(userId: String): Flow<SalonPreference?> = flow {
        val docRef = db.collection("users").document(userId)
            .collection("salon_preferences").document("profile")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SalonPreference::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    override suspend fun saveSalonPreference(
        savedCustomerName: String,
        savedPhone: String,
        barber2CustomName: String,
        barber3CustomName: String,
        salonWhatsappPhone: String,
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("salon_preferences").document("profile")
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "savedCustomerName" to savedCustomerName.trim().take(80),
                        "savedPhone" to savedPhone.trim().take(20),
                        "barber2CustomName" to barber2CustomName.trim().take(50),
                        "barber3CustomName" to barber3CustomName.trim().take(50),
                        "salonWhatsappPhone" to salonWhatsappPhone.trim().ifEmpty { SalonCatalog.DEFAULT_SALON_PHONE }.take(20),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "savedCustomerName" to savedCustomerName.trim().take(80),
                        "savedPhone" to savedPhone.trim().take(20),
                        "barber2CustomName" to barber2CustomName.trim().take(50),
                        "barber3CustomName" to barber3CustomName.trim().take(50),
                        "salonWhatsappPhone" to salonWhatsappPhone.trim().ifEmpty { SalonCatalog.DEFAULT_SALON_PHONE }.take(20),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }
}
