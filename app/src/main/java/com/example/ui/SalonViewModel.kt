package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Appointment
import com.example.data.model.BarberProfile
import com.example.data.model.HaircutStyle
import com.example.data.model.SalonCatalog
import com.example.data.model.SalonPackage
import com.example.data.model.SalonPreference
import com.example.data.repository.SalonBookingDataSource
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DateOption(
    val isoDate: String,
    val dayNameAr: String,
    val dayNumber: String,
    val monthAr: String,
    val year: Int,
    val monthZeroBased: Int,
    val dayOfMonth: Int,
) {
    val fullLabel: String
        get() = "$dayNameAr $dayNumber $monthAr"
}

data class TimeSlotOption(
    val id: String,
    val displayAr: String,
    val hour24: Int,
    val minute: Int,
)

data class BookingDraftState(
    val customerName: String = "",
    val customerPhone: String = "",
    val selectedBarberId: String = "mody",
    val selectedPackageId: String = "vip_royal",
    val selectedHaircutName: String = "",
    val selectedDateIndex: Int = 0,
    val selectedTimeIndex: Int = 4,
    val reminderEnabled: Boolean = true,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val validationError: String? = null,
    val confirmedAppointment: Appointment? = null,
)

class SalonViewModel(
    private val repository: SalonBookingDataSource,
    private val currentUserId: String,
    defaultDisplayName: String = "",
    defaultPhone: String = "",
    val isSalonOwner: Boolean = false,
) : ViewModel() {

    val availableDates: List<DateOption> = buildNextSevenDays()
    val availableTimeSlots: List<TimeSlotOption> = buildSalonTimeSlots()

    private val _bookingDraft = MutableStateFlow(
        BookingDraftState(
            customerName = defaultDisplayName,
            customerPhone = defaultPhone,
        )
    )
    val bookingDraft: StateFlow<BookingDraftState> = _bookingDraft.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val appointmentsState: StateFlow<UiState<List<Appointment>>> =
        repository.observeAppointments(currentUserId)
            .map<List<Appointment>, UiState<List<Appointment>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing appointments", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل المواعيد"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading,
            )

    val ownerBookingsState: StateFlow<UiState<List<Appointment>>> =
        if (isSalonOwner) {
            repository.observeAllSalonBookingsForOwner()
                .map<List<Appointment>, UiState<List<Appointment>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing owner salon bookings", rootError)
                    emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل جدول حجوزات الصالون"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = UiState.Loading,
                )
        } else {
            MutableStateFlow<UiState<List<Appointment>>>(UiState.Success(emptyList())).asStateFlow()
        }

    val preferenceState: StateFlow<SalonPreference?> =
        repository.observeSalonPreference(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing preferences", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null,
            )

    init {
        viewModelScope.launch {
            preferenceState.collect { pref ->
                if (pref != null) {
                    _bookingDraft.update { current ->
                        current.copy(
                            customerName = current.customerName.ifBlank { pref.savedCustomerName },
                            customerPhone = current.customerPhone.ifBlank { pref.savedPhone },
                        )
                    }
                }
            }
        }
    }

    fun updateCustomerName(name: String) {
        _bookingDraft.update { it.copy(customerName = name.take(80), validationError = null) }
    }

    fun updateCustomerPhone(phone: String) {
        val filtered = phone.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }
        _bookingDraft.update { it.copy(customerPhone = filtered.take(16), validationError = null) }
    }

    fun selectBarber(barberId: String) {
        _bookingDraft.update { it.copy(selectedBarberId = barberId, validationError = null) }
    }

    fun selectPackage(packageId: String) {
        _bookingDraft.update { it.copy(selectedPackageId = packageId, validationError = null) }
    }

    fun selectHaircutStyle(style: HaircutStyle?) {
        _bookingDraft.update {
            it.copy(selectedHaircutName = style?.fullDisplayName ?: "", validationError = null)
        }
    }

    fun selectDateIndex(index: Int) {
        if (index in availableDates.indices) {
            _bookingDraft.update { it.copy(selectedDateIndex = index, validationError = null) }
        }
    }

    fun selectTimeIndex(index: Int) {
        if (index in availableTimeSlots.indices) {
            _bookingDraft.update { it.copy(selectedTimeIndex = index, validationError = null) }
        }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        _bookingDraft.update { it.copy(reminderEnabled = enabled) }
    }

    fun updateNotes(notes: String) {
        _bookingDraft.update { it.copy(notes = notes.take(300)) }
    }

    fun dismissConfirmedSheet() {
        _bookingDraft.update { it.copy(confirmedAppointment = null) }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun submitBooking(
        barbers: List<BarberProfile>,
        onSuccess: (Appointment) -> Unit,
    ) {
        val draft = _bookingDraft.value
        val trimmedName = draft.customerName.trim()
        val validEgyptianE164 = com.example.auth.AuthManager.normalizePhoneToE164(draft.customerPhone)
        if (trimmedName.length < 2) {
            _bookingDraft.update { it.copy(validationError = "يرجى كتابة اسم الزبون (حرفين على الأقل)") }
            return
        }
        if (validEgyptianE164 == null) {
            _bookingDraft.update {
                it.copy(validationError = "يرجى إدخال رقم موبايل مصري صحيح (010 / 011 / 012 / 015 مكوّن من 11 رقماً)")
            }
            return
        }

        val barber = barbers.find { it.id == draft.selectedBarberId } ?: barbers.first()
        val pkg: SalonPackage = SalonCatalog.packages.find { it.id == draft.selectedPackageId }
            ?: SalonCatalog.packages.first()
        val dateOpt = availableDates.getOrElse(draft.selectedDateIndex) { availableDates.first() }
        val timeOpt = availableTimeSlots.getOrElse(draft.selectedTimeIndex) { availableTimeSlots.first() }

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dateOpt.year)
            set(Calendar.MONTH, dateOpt.monthZeroBased)
            set(Calendar.DAY_OF_MONTH, dateOpt.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, timeOpt.hour24)
            set(Calendar.MINUTE, timeOpt.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        _bookingDraft.update { it.copy(isSubmitting = true, validationError = null) }

        viewModelScope.launch {
            try {
                val created = repository.createAppointment(
                    Appointment(
                        userId = currentUserId,
                        customerName = trimmedName,
                        customerPhone = draft.customerPhone.trim(),
                        barberId = barber.id,
                        barberName = barber.defaultName,
                        packageId = pkg.id,
                        packageTitle = pkg.title,
                        packageServices = pkg.servicesSummary,
                        selectedHaircut = draft.selectedHaircutName,
                        appointmentDate = dateOpt.fullLabel,
                        appointmentTime = timeOpt.displayAr,
                        appointmentEpochMillis = cal.timeInMillis,
                        notes = draft.notes.trim(),
                        status = "confirmed",
                        reminderScheduled = draft.reminderEnabled,
                    )
                )

                // Also persist customer name & phone for quick future bookings
                val currentPref = preferenceState.value
                runCatching {
                    repository.saveSalonPreference(
                        savedCustomerName = trimmedName,
                        savedPhone = draft.customerPhone.trim(),
                        barber2CustomName = currentPref?.barber2CustomName ?: "",
                        barber3CustomName = currentPref?.barber3CustomName ?: "",
                        salonWhatsappPhone = currentPref?.salonWhatsappPhone ?: SalonCatalog.DEFAULT_SALON_PHONE,
                    )
                }

                _bookingDraft.update {
                    it.copy(
                        isSubmitting = false,
                        notes = "",
                        confirmedAppointment = created,
                    )
                }
                onSuccess(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bookingDraft.update {
                    it.copy(
                        isSubmitting = false,
                        validationError = e.localizedMessage ?: "حدث خطأ أثناء حفظ الحجز، حاول مرة أخرى",
                    )
                }
            }
        }
    }

    fun markAppointmentCompleted(appointmentId: String) {
        viewModelScope.launch {
            try {
                repository.updateAppointmentStatus(appointmentId, "completed", reminderScheduled = false)
                _actionMessage.value = "تم تحديث حالة الموعد إلى مكتمل"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر تحديث حالة الموعد"
            }
        }
    }

    fun updateOwnerBookingStatus(appointment: Appointment, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateBookingStatusByOwner(appointment, newStatus)
                _actionMessage.value = if (newStatus == "completed") {
                    "تم تسجيل إتمام الحلاقة للزبون ${appointment.customerName} بنجاح"
                } else {
                    "تم إلغاء موعد الزبون ${appointment.customerName}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر تحديث حالة الحجز من لوحة الإدارة"
            }
        }
    }

    fun cancelAppointment(appointmentId: String, onCancelled: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateAppointmentStatus(appointmentId, "cancelled", reminderScheduled = false)
                onCancelled()
                _actionMessage.value = "تم إلغاء الموعد وإيقاف التذكير"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر إلغاء الموعد"
            }
        }
    }

    fun submitAppointmentFeedback(
        appointmentId: String,
        rating: Int,
        feedbackComment: String,
    ) {
        if (rating !in 1..5) {
            _actionMessage.value = "يرجى اختيار تقييم من نجمة إلى 5 نجوم"
            return
        }
        viewModelScope.launch {
            try {
                repository.submitAppointmentFeedback(
                    appointmentId = appointmentId,
                    rating = rating,
                    feedbackComment = feedbackComment,
                )
                _actionMessage.value = "شكراً لتقييمك! تم حفظ رأيك في سجل الحجز بنجاح"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر حفظ التقييم، حاول مرة أخرى"
            }
        }
    }

    fun deleteAppointment(appointmentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAppointment(appointmentId)
                _actionMessage.value = "تم حذف الحجز من السجل"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر حذف الحجز"
            }
        }
    }

    fun saveSalonSettings(
        savedCustomerName: String,
        savedPhone: String,
        barber2Name: String,
        barber3Name: String,
        salonWhatsappPhone: String,
    ) {
        viewModelScope.launch {
            try {
                repository.saveSalonPreference(
                    savedCustomerName = savedCustomerName,
                    savedPhone = savedPhone,
                    barber2CustomName = barber2Name,
                    barber3CustomName = barber3Name,
                    salonWhatsappPhone = salonWhatsappPhone,
                )
                _actionMessage.value = "تم حفظ إعدادات الصالون والصنايعية بنجاح"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "تعذر حفظ الإعدادات"
            }
        }
    }

    private fun buildNextSevenDays(): List<DateOption> {
        val arabicLocale = Locale.forLanguageTag("ar")
        val dayFmt = SimpleDateFormat("EEEE", arabicLocale)
        val dayNumFmt = SimpleDateFormat("d", arabicLocale)
        val monthFmt = SimpleDateFormat("MMMM", arabicLocale)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return (0 until 7).map { offset ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayLabel = when (offset) {
                0 -> "اليوم (${dayFmt.format(cal.time)})"
                1 -> "غداً (${dayFmt.format(cal.time)})"
                else -> dayFmt.format(cal.time)
            }
            DateOption(
                isoDate = isoFmt.format(cal.time),
                dayNameAr = dayLabel,
                dayNumber = dayNumFmt.format(cal.time),
                monthAr = monthFmt.format(cal.time),
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }

    private fun buildSalonTimeSlots(): List<TimeSlotOption> {
        // Salon hours from 1:00 PM (13:00) to 11:30 PM (23:30)
        val slots = mutableListOf<TimeSlotOption>()
        for (hour in 13..23) {
            for (minute in listOf(0, 30)) {
                val hour12 = if (hour > 12) hour - 12 else hour
                val minStr = if (minute == 0) "00" else "30"
                val suffix = "مساءً"
                slots.add(
                    TimeSlotOption(
                        id = "${hour}_$minStr",
                        displayAr = "$hour12:$minStr $suffix",
                        hour24 = hour,
                        minute = minute,
                    )
                )
            }
        }
        return slots
    }

    private companion object {
        const val TAG = "SalonViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
