package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog

enum class BookingsFilterTab(val labelRes: Int) {
    ALL(R.string.bookings_tab_all),
    UPCOMING(R.string.bookings_tab_upcoming),
    PAST(R.string.bookings_tab_past),
}

@Composable
fun BookingsSummaryStatsRow(
    upcomingCount: Int,
    completedOrPastCount: Int,
    cancelledCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bookings_stats_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BookingStatTile(
            count = upcomingCount,
            label = stringResource(R.string.bookings_stat_upcoming),
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        BookingStatTile(
            count = completedOrPastCount,
            label = stringResource(R.string.bookings_stat_completed),
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        BookingStatTile(
            count = cancelledCount,
            label = stringResource(R.string.bookings_stat_cancelled),
            accentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookingStatTile(
    count: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BookingReservationCard(
    appointment: Appointment,
    isUpcoming: Boolean,
    salonPhone: String,
    onWhatsAppSalon: () -> Unit,
    onWhatsAppCustomer: () -> Unit,
    onSmsCustomer: () -> Unit,
    onPreviewNotification: () -> Unit,
    onMarkCompleted: () -> Unit,
    onRequestCancel: () -> Unit,
    onDeleteRecord: () -> Unit,
    onSubmitFeedback: (rating: Int, comment: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isConfirmed = appointment.status == "confirmed"
    val statusLabel = when {
        appointment.status == "completed" -> stringResource(R.string.status_completed)
        appointment.status == "cancelled" -> stringResource(R.string.status_cancelled)
        isUpcoming -> stringResource(R.string.status_upcoming_confirmed)
        else -> stringResource(R.string.status_past_due_confirmed)
    }

    val statusContainerColor = when {
        appointment.status == "completed" -> MaterialTheme.colorScheme.tertiaryContainer
        appointment.status == "cancelled" -> MaterialTheme.colorScheme.errorContainer
        isUpcoming -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContentColor = when {
        appointment.status == "completed" -> MaterialTheme.colorScheme.onTertiaryContainer
        appointment.status == "cancelled" -> MaterialTheme.colorScheme.onErrorContainer
        isUpcoming -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("appointment_card_${appointment.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirmed && isUpcoming) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = BorderStroke(
            width = if (isConfirmed && isUpcoming) 1.5.dp else 1.dp,
            color = when {
                isConfirmed && isUpcoming -> MaterialTheme.colorScheme.primary
                appointment.status == "completed" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${appointment.appointmentDate} • ${appointment.appointmentTime}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (appointment.status == "cancelled") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = "${appointment.customerName} (\u200E${appointment.customerPhone})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    color = statusContainerColor,
                    shape = CircleShape,
                    modifier = Modifier.testTag("appt_status_badge_${appointment.id}"),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusContentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Text(
                text = "🧔 الصنايعي: ${appointment.barberName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "✨ الباكيدج: ${appointment.packageTitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (appointment.selectedHaircut.isNotBlank()) {
                Text(
                    text = "💈 القصة: ${appointment.selectedHaircut}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (appointment.notes.isNotBlank()) {
                Text(
                    text = "📝 ملاحظات: ${appointment.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "📞 رقم المحل: \u200E${SalonCatalog.SHOP_DIRECT_PHONE} • واتساب وحجز: \u200E$salonPhone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isConfirmed) {
                // Active reservation actions: WhatsApp Salon Confirmation + Share + Status/Cancel
                Button(
                    onClick = onWhatsAppSalon,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("appt_whatsapp_salon_${appointment.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatsapp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تأكيد الموعد عبر واتساب الصالون (\u200E$salonPhone)", maxLines = 1)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onWhatsAppCustomer,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("appt_whatsapp_${appointment.id}"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Whatsapp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("واتساب الزبون", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onSmsCustomer,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("appt_sms_${appointment.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("رسالة SMS", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onPreviewNotification,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("appt_notif_${appointment.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "معاينة إشعار الـ 30 دقيقة",
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الإشعار", maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onMarkCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("appt_complete_${appointment.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تمت الحلاقة")
                    }

                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("appt_cancel_${appointment.id}"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إلغاء الموعد")
                    }
                }
            } else {
                if (appointment.status == "completed") {
                    CompletedAppointmentFeedbackSection(
                        appointment = appointment,
                        onSubmitFeedback = onSubmitFeedback,
                    )
                }

                // Past/terminal record action: allow removing from history
                OutlinedButton(
                    onClick = onDeleteRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("appt_delete_${appointment.id}"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete_booking_record))
                }
            }
        }
    }
}

@Composable
fun CancelAppointmentConfirmDialog(
    appointment: Appointment,
    onConfirmCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("cancel_appointment_dialog"),
        title = {
            Text(
                text = stringResource(R.string.cancel_dialog_title),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "هل أنت متأكد من إلغاء موعدك مع ${appointment.barberName} يوم ${appointment.appointmentDate} الساعة ${appointment.appointmentTime}؟ سيتم أيضاً إيقاف إشعار التذكير.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_cancel_dialog_button"),
            ) {
                Text(stringResource(R.string.cancel_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dismiss_cancel_dialog_button"),
            ) {
                Text(stringResource(R.string.cancel_dialog_dismiss))
            }
        },
    )
}

@Composable
fun SalonSettingsExpandableCard(
    barber2Input: String,
    barber3Input: String,
    salonPhoneInput: String,
    onBarber2Change: (String) -> Unit,
    onBarber3Change: (String) -> Unit,
    onSalonPhoneChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "إعدادات الصالون وأسماء الصنايعية",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "اترك خانة صنايعي 2 وصنايعي 3 فارغة ليظهرا بدون اسم حالياً، أو اكتب الاسم عند انضمام صنايعي جديد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = barber2Input,
                onValueChange = { onBarber2Change(it.take(40)) },
                label = { Text("اسم صنايعي 2 (اختياري)") },
                placeholder = { Text("بدون اسم حالياً (صنايعي 2)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_barber2_input"),
            )

            OutlinedTextField(
                value = barber3Input,
                onValueChange = { onBarber3Change(it.take(40)) },
                label = { Text("اسم صنايعي 3 (اختياري)") },
                placeholder = { Text("بدون اسم حالياً (صنايعي 3)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_barber3_input"),
            )

            OutlinedTextField(
                value = salonPhoneInput,
                onValueChange = { ch ->
                    onSalonPhoneChange(ch.filter { it.isDigit() || it == '+' }.take(16))
                },
                label = { Text("رقم الحجز والواتساب (رقم المحل الثابت: ${SalonCatalog.SHOP_DIRECT_PHONE})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_salon_phone_input"),
            )

            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_salon_settings_button"),
            ) {
                Text("حفظ الإعدادات")
            }
        }
    }
}

@Composable
fun EmptyBookingsCard(
    title: String,
    subtitle: String,
    onBookNewClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_appointments_card"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onBookNewClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_book_now_button"),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("احجز موعدك الآن")
            }
        }
    }
}
