package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.BarberProfile
import com.example.ui.UiState
import com.example.util.MessageShareHelper
import java.util.Calendar

enum class OwnerDayScope(val id: String, val labelAr: String) {
    TODAY("today", "حجوزات اليوم"),
    TOMORROW("tomorrow", "حجوزات الغد"),
    ALL("all", "كل الحجوزات"),
}

internal fun isSameCalendarDayOffset(
    epochMillis: Long,
    referenceNowMillis: Long,
    dayOffset: Int,
): Boolean {
    if (epochMillis <= 0L) return false
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = referenceNowMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }
    val apptCal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
    }
    return targetCal.get(Calendar.YEAR) == apptCal.get(Calendar.YEAR) &&
        targetCal.get(Calendar.DAY_OF_YEAR) == apptCal.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun OwnerDashboardScreen(
    ownerBookingsState: UiState<List<Appointment>>,
    barbers: List<BarberProfile>,
    ownerIdentity: String?,
    onMarkCompleted: (Appointment) -> Unit,
    onCancelBooking: (Appointment) -> Unit,
    onNavigateBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    referenceNowMillis: Long = System.currentTimeMillis(),
) {
    BackHandler(onBack = onNavigateBackHome)
    val context = LocalContext.current

    var selectedDayScope by rememberSaveable { mutableStateOf(OwnerDayScope.TODAY) }
    var selectedBarberFilter by rememberSaveable { mutableStateOf("all") }

    val allBookings = (ownerBookingsState as? UiState.Success)?.data.orEmpty()

    val dayScopedBookings = remember(allBookings, selectedDayScope, referenceNowMillis) {
        when (selectedDayScope) {
            OwnerDayScope.TODAY -> allBookings.filter {
                isSameCalendarDayOffset(it.appointmentEpochMillis, referenceNowMillis, dayOffset = 0)
            }
            OwnerDayScope.TOMORROW -> allBookings.filter {
                isSameCalendarDayOffset(it.appointmentEpochMillis, referenceNowMillis, dayOffset = 1)
            }
            OwnerDayScope.ALL -> allBookings
        }.sortedBy { it.appointmentEpochMillis }
    }

    val filteredBookings = remember(dayScopedBookings, selectedBarberFilter) {
        if (selectedBarberFilter == "all") {
            dayScopedBookings
        } else {
            dayScopedBookings.filter { it.barberId == selectedBarberFilter }
        }
    }

    val confirmedCount = remember(dayScopedBookings) {
        dayScopedBookings.count { it.status == "confirmed" }
    }
    val completedCount = remember(dayScopedBookings) {
        dayScopedBookings.count { it.status == "completed" }
    }
    val cancelledCount = remember(dayScopedBookings) {
        dayScopedBookings.count { it.status == "cancelled" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("owner_dashboard_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("owner_bookings_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Salon Owner Header Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "لوحة إدارة صالون أبو العطا",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "خاصة بصاحب الصالون فقط • متابعة حجوزات اليوم لحظياً",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!ownerIdentity.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "حساب المالك الموثّق: \u200E$ownerIdentity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 2. Day Scope Selector (Today / Tomorrow / All)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OwnerDayScope.entries.forEach { scope ->
                        val selected = selectedDayScope == scope
                        FilterChip(
                            selected = selected,
                            onClick = { selectedDayScope = scope },
                            label = {
                                Text(
                                    text = scope.labelAr,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("owner_filter_${scope.id}"),
                        )
                    }
                }
            }

            // 3. Summary Counters Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OwnerStatCard(
                        title = "إجمالي",
                        count = dayScopedBookings.size,
                        tag = "owner_stat_total",
                        modifier = Modifier.weight(1f),
                    )
                    OwnerStatCard(
                        title = "قيد الانتظار",
                        count = confirmedCount,
                        tag = "owner_stat_confirmed",
                        highlightPrimary = true,
                        modifier = Modifier.weight(1f),
                    )
                    OwnerStatCard(
                        title = "مكتملة",
                        count = completedCount,
                        tag = "owner_stat_completed",
                        modifier = Modifier.weight(1f),
                    )
                    OwnerStatCard(
                        title = "ملغاة",
                        count = cancelledCount,
                        tag = "owner_stat_cancelled",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 4. Barber Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedBarberFilter == "all",
                        onClick = { selectedBarberFilter = "all" },
                        label = { Text("كل الصنايعية") },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("owner_barber_all"),
                    )
                    barbers.forEach { barber ->
                        FilterChip(
                            selected = selectedBarberFilter == barber.id,
                            onClick = { selectedBarberFilter = barber.id },
                            label = { Text(barber.defaultName) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("owner_barber_${barber.id}"),
                        )
                    }
                }
            }

            // 5. Bookings Content
            when (ownerBookingsState) {
                is UiState.Loading -> {
                    item {
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
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                text = ownerBookingsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredBookings.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_empty_bookings_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EventAvailable,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        text = when (selectedDayScope) {
                                            OwnerDayScope.TODAY -> "لا توجد حجوزات مسجّلة لليوم حتى الآن"
                                            OwnerDayScope.TOMORROW -> "لا توجد حجوزات مسجّلة ليوم الغد حتى الآن"
                                            OwnerDayScope.ALL -> "لا توجد حجوزات مسجّلة في السجل بعد"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        text = "بمجرد قيام أي زبون بتأكيد حجزه عبر التطبيق سيظهر هنا تلقائياً بترتيب المواعيد.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredBookings, key = { it.id }) { appt ->
                            OwnerBookingCard(
                                appointment = appt,
                                onCallCustomer = {
                                    MessageShareHelper.dialPhone(context, appt.customerPhone)
                                },
                                onWhatsAppCustomer = {
                                    MessageShareHelper.sendWhatsAppToCustomer(context, appt)
                                },
                                onMarkCompleted = { onMarkCompleted(appt) },
                                onCancelBooking = { onCancelBooking(appt) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerStatCard(
    title: String,
    count: Int,
    tag: String,
    modifier: Modifier = Modifier,
    highlightPrimary: Boolean = false,
) {
    Surface(
        modifier = modifier.testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = if (highlightPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (highlightPrimary) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OwnerBookingCard(
    appointment: Appointment,
    onCallCustomer: () -> Unit,
    onWhatsAppCustomer: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCancelBooking: () -> Unit,
) {
    val statusLabel = when (appointment.status) {
        "completed" -> "مكتمل ✓"
        "cancelled" -> "ملغى"
        else -> "مؤكد • بالانتظار"
    }
    val statusColor = when (appointment.status) {
        "completed" -> MaterialTheme.colorScheme.tertiary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_booking_card_${appointment.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = statusColor.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Row: Time Slot Badge + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "${appointment.appointmentTime} • ${appointment.appointmentDate}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Customer & Barber Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${appointment.customerName}  •  \u200E${appointment.customerPhone}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "الصنايعي: ${appointment.barberName}  |  ${appointment.packageTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (appointment.selectedHaircut.isNotBlank()) {
                Text(
                    text = "القصة المختارة: ${appointment.selectedHaircut}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (appointment.notes.isNotBlank()) {
                Text(
                    text = "ملاحظات الزبون: ${appointment.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (appointment.rating in 1..5) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "تقييم الزبون: ${appointment.rating}/5" +
                            if (appointment.feedbackComment.isNotBlank()) " — \"${appointment.feedbackComment}\"" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Direct Contact Buttons (Call + WhatsApp Customer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onCallCustomer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("owner_call_${appointment.id}"),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("اتصال بالزبون", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onWhatsAppCustomer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("owner_whatsapp_${appointment.id}"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("واتساب الزبون", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Status Management Buttons (for Confirmed appointments)
            if (appointment.status == "confirmed") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onMarkCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("owner_complete_${appointment.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تمت الحلاقة ✓", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancelBooking,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("owner_cancel_${appointment.id}"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("إلغاء", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
