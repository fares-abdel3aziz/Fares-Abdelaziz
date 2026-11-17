package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog
import com.example.data.model.SalonPreference
import com.example.notifications.ReminderScheduler
import com.example.ui.UiState
import com.example.util.MessageShareHelper

private const val UPCOMING_GRACE_WINDOW_MS = 60L * 60L * 1000L // 1 hour grace window

fun isUpcomingReservation(appointment: Appointment, nowMillis: Long = System.currentTimeMillis()): Boolean {
    return appointment.status == "confirmed" &&
        appointment.appointmentEpochMillis >= (nowMillis - UPCOMING_GRACE_WINDOW_MS)
}

@Composable
fun AppointmentsScreen(
    appointmentsState: UiState<List<Appointment>>,
    preference: SalonPreference?,
    userEmail: String?,
    onMarkCompleted: (Appointment) -> Unit,
    onCancelAppointment: (Appointment) -> Unit,
    onDeleteAppointment: (Appointment) -> Unit = {},
    onSubmitFeedback: (Appointment, Int, String) -> Unit = { _, _, _ -> },
    onSaveSettings: (String, String, String, String, String) -> Unit,
    onBookNewClick: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    referenceNowMillis: Long = System.currentTimeMillis(),
) {
    BackHandler(onBack = onNavigateBackHome)

    val context = LocalContext.current
    val salonPhone = preference?.salonWhatsappPhone?.ifBlank { SalonCatalog.DEFAULT_SALON_PHONE }
        ?: SalonCatalog.DEFAULT_SALON_PHONE

    var selectedTab by rememberSaveable { mutableStateOf(BookingsFilterTab.ALL) }
    var pendingCancelAppointment by remember { mutableStateOf<Appointment?>(null) }

    var showSettingsCard by remember { mutableStateOf(false) }
    var barber2Input by remember { mutableStateOf(preference?.barber2CustomName ?: "") }
    var barber3Input by remember { mutableStateOf(preference?.barber3CustomName ?: "") }
    var salonPhoneInput by remember {
        mutableStateOf(preference?.salonWhatsappPhone ?: SalonCatalog.DEFAULT_SALON_PHONE)
    }

    LaunchedEffect(preference) {
        if (preference != null) {
            barber2Input = preference.barber2CustomName
            barber3Input = preference.barber3CustomName
            salonPhoneInput = preference.salonWhatsappPhone.ifBlank { SalonCatalog.DEFAULT_SALON_PHONE }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "تم تفعيل الإشعارات، اضغط معاينة الإشعار للتجربة", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("appointments_lazy_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. My Bookings Header + Salon Settings Button
            item(key = "bookings_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.my_bookings_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.my_bookings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { showSettingsCard = !showSettingsCard },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_salon_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الإعدادات")
                    }
                }
            }

            if (showSettingsCard) {
                item(key = "salon_settings") {
                    SalonSettingsExpandableCard(
                        barber2Input = barber2Input,
                        barber3Input = barber3Input,
                        salonPhoneInput = salonPhoneInput,
                        onBarber2Change = { barber2Input = it },
                        onBarber3Change = { barber3Input = it },
                        onSalonPhoneChange = { salonPhoneInput = it },
                        onSaveClick = {
                            onSaveSettings(
                                preference?.savedCustomerName ?: "",
                                preference?.savedPhone ?: "",
                                barber2Input,
                                barber3Input,
                                salonPhoneInput.ifBlank { SalonCatalog.DEFAULT_SALON_PHONE },
                            )
                            showSettingsCard = false
                        },
                    )
                }
            }

            when (appointmentsState) {
                is UiState.Loading -> {
                    item(key = "loading_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error_state") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                text = appointmentsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val allBookings = appointmentsState.data
                    val upcomingList = allBookings
                        .filter { isUpcomingReservation(it, referenceNowMillis) }
                        .sortedBy { it.appointmentEpochMillis }
                    val pastAndCancelledList = allBookings
                        .filterNot { isUpcomingReservation(it, referenceNowMillis) }
                        .sortedByDescending { it.appointmentEpochMillis }

                    val completedOrPastCount = pastAndCancelledList.count { it.status != "cancelled" }
                    val cancelledCount = allBookings.count { it.status == "cancelled" }

                    // 2. Summary Stats + Upcoming / Past Filter Tabs
                    item(key = "stats_and_tabs") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BookingsSummaryStatsRow(
                                upcomingCount = upcomingList.size,
                                completedOrPastCount = completedOrPastCount,
                                cancelledCount = cancelledCount,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BookingsFilterTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    val badgeCount = when (tab) {
                                        BookingsFilterTab.ALL -> allBookings.size
                                        BookingsFilterTab.UPCOMING -> upcomingList.size
                                        BookingsFilterTab.PAST -> pastAndCancelledList.size
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        label = {
                                            Text("${stringResource(tab.labelRes)} ($badgeCount)")
                                        },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("bookings_filter_${tab.name.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    val showUpcoming = selectedTab == BookingsFilterTab.ALL || selectedTab == BookingsFilterTab.UPCOMING
                    val showPast = selectedTab == BookingsFilterTab.ALL || selectedTab == BookingsFilterTab.PAST

                    val visibleCount = (if (showUpcoming) upcomingList.size else 0) +
                        (if (showPast) pastAndCancelledList.size else 0)

                    if (visibleCount == 0) {
                        item(key = "empty_filtered") {
                            val (emptyTitle, emptySub) = when (selectedTab) {
                                BookingsFilterTab.UPCOMING -> "لا توجد مواعيد قادمة حالياً" to
                                    "احجز موعدك القادم مع كابتن مودي أو فريق الصنايعية ليصلك تذكير تلقائي قبل الموعد."
                                BookingsFilterTab.PAST -> "لا توجد حجوزات سابقة أو ملغاة" to
                                    "ستظهر هنا زياراتك المكتملة وسجل مواعيدك السابقة."
                                BookingsFilterTab.ALL -> stringResource(R.string.empty_appointments_title) to
                                    stringResource(R.string.empty_appointments_subtitle)
                            }
                            EmptyBookingsCard(
                                title = emptyTitle,
                                subtitle = emptySub,
                                onBookNewClick = onBookNewClick,
                            )
                        }
                    } else {
                        if (showUpcoming && upcomingList.isNotEmpty()) {
                            item(key = "upcoming_section_header") {
                                Text(
                                    text = stringResource(R.string.bookings_section_upcoming),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            items(
                                items = upcomingList,
                                key = { "upcoming_${it.id}" },
                            ) { appointment ->
                                BookingReservationCard(
                                    appointment = appointment,
                                    isUpcoming = true,
                                    salonPhone = salonPhone,
                                    onWhatsAppSalon = {
                                        MessageShareHelper.launchWhatsAppSalonConfirmation(
                                            context = context,
                                            appointment = appointment,
                                            salonPhone = salonPhone,
                                        )
                                    },
                                    onWhatsAppCustomer = {
                                        MessageShareHelper.sendWhatsAppMessage(
                                            context = context,
                                            targetPhone = appointment.customerPhone,
                                            message = MessageShareHelper.formatAppointmentMessage(appointment, salonPhone),
                                        )
                                    },
                                    onSmsCustomer = {
                                        MessageShareHelper.sendSmsMessage(
                                            context = context,
                                            targetPhone = appointment.customerPhone,
                                            message = MessageShareHelper.formatAppointmentMessage(appointment, salonPhone),
                                        )
                                    },
                                    onPreviewNotification = {
                                        val shown = ReminderScheduler.triggerInstantPreviewReminder(context, appointment)
                                        if (shown) {
                                            Toast.makeText(
                                                context,
                                                "وصلك الآن إشعار التذكير قبل الميعاد بنصف ساعة!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onMarkCompleted = { onMarkCompleted(appointment) },
                                    onRequestCancel = { pendingCancelAppointment = appointment },
                                    onDeleteRecord = { onDeleteAppointment(appointment) },
                                    onSubmitFeedback = { rating, comment ->
                                        onSubmitFeedback(appointment, rating, comment)
                                    },
                                )
                            }
                        }

                        if (showPast && pastAndCancelledList.isNotEmpty()) {
                            item(key = "past_section_header") {
                                Text(
                                    text = stringResource(R.string.bookings_section_past),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            items(
                                items = pastAndCancelledList,
                                key = { "past_${it.id}" },
                            ) { appointment ->
                                BookingReservationCard(
                                    appointment = appointment,
                                    isUpcoming = false,
                                    salonPhone = salonPhone,
                                    onWhatsAppSalon = {
                                        MessageShareHelper.launchWhatsAppSalonConfirmation(
                                            context = context,
                                            appointment = appointment,
                                            salonPhone = salonPhone,
                                        )
                                    },
                                    onWhatsAppCustomer = {
                                        MessageShareHelper.sendWhatsAppMessage(
                                            context = context,
                                            targetPhone = appointment.customerPhone,
                                            message = MessageShareHelper.formatAppointmentMessage(appointment, salonPhone),
                                        )
                                    },
                                    onSmsCustomer = {
                                        MessageShareHelper.sendSmsMessage(
                                            context = context,
                                            targetPhone = appointment.customerPhone,
                                            message = MessageShareHelper.formatAppointmentMessage(appointment, salonPhone),
                                        )
                                    },
                                    onPreviewNotification = {
                                        ReminderScheduler.triggerInstantPreviewReminder(context, appointment)
                                    },
                                    onMarkCompleted = { onMarkCompleted(appointment) },
                                    onRequestCancel = { pendingCancelAppointment = appointment },
                                    onDeleteRecord = { onDeleteAppointment(appointment) },
                                    onSubmitFeedback = { rating, comment ->
                                        onSubmitFeedback(appointment, rating, comment)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Sign Out Footer
            item(key = "sign_out_footer") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }

    val toCancel = pendingCancelAppointment
    if (toCancel != null) {
        CancelAppointmentConfirmDialog(
            appointment = toCancel,
            onConfirmCancel = {
                pendingCancelAppointment = null
                onCancelAppointment(toCancel)
            },
            onDismiss = {
                pendingCancelAppointment = null
            },
        )
    }
}
