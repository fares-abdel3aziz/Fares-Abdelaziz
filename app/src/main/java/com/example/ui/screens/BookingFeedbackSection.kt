package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Appointment

@Composable
fun CompletedAppointmentFeedbackSection(
    appointment: Appointment,
    onSubmitFeedback: (rating: Int, comment: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedRating = appointment.rating.toInt().coerceIn(0, 5)
    var isEditing by rememberSaveable(appointment.id) { mutableStateOf(savedRating == 0) }
    var selectedRating by rememberSaveable(appointment.id) {
        mutableIntStateOf(if (savedRating in 1..5) savedRating else 5)
    }
    var commentText by rememberSaveable(appointment.id) {
        mutableStateOf(appointment.feedbackComment)
    }

    LaunchedEffect(appointment.rating, appointment.feedbackComment) {
        val updatedRating = appointment.rating.toInt().coerceIn(0, 5)
        if (updatedRating in 1..5) {
            selectedRating = updatedRating
            commentText = appointment.feedbackComment
            isEditing = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(
                if (!isEditing && savedRating in 1..5) {
                    "feedback_saved_${appointment.id}"
                } else {
                    "feedback_form_${appointment.id}"
                }
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        ),
    ) {
        if (!isEditing && savedRating in 1..5) {
            // Saved Review Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feedback_saved_badge),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (star <= savedRating) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = null,
                                    tint = if (star <= savedRating) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ratingLabelArabic(savedRating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    TextButton(
                        onClick = { isEditing = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("feedback_edit_${appointment.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.feedback_edit_btn))
                    }
                }

                if (appointment.feedbackComment.isNotBlank()) {
                    Text(
                        text = "«${appointment.feedbackComment}»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            // Interactive 1–5 Star Feedback Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.feedback_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.feedback_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { star ->
                            val isFilled = star <= selectedRating
                            IconButton(
                                onClick = { selectedRating = star },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("feedback_star_${appointment.id}_$star"),
                            ) {
                                Icon(
                                    imageVector = if (isFilled) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = "تقييم $star نجوم",
                                    tint = if (isFilled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = ratingLabelArabic(selectedRating),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it.take(300) },
                    label = { Text(stringResource(R.string.feedback_comment_label)) },
                    placeholder = { Text(stringResource(R.string.feedback_comment_hint)) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_comment_${appointment.id}"),
                )

                Button(
                    onClick = {
                        onSubmitFeedback(selectedRating, commentText.trim())
                        isEditing = false
                    },
                    enabled = selectedRating in 1..5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("feedback_submit_${appointment.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.feedback_submit_btn),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun ratingLabelArabic(stars: Int): String = when (stars) {
    1 -> "١ / ٥ (ضعيف)"
    2 -> "٢ / ٥ (مقبول)"
    3 -> "٣ / ٥ (جيد)"
    4 -> "٤ / ٥ (جيد جداً)"
    5 -> "٥ / ٥ (ممتاز 🌟)"
    else -> ""
}
