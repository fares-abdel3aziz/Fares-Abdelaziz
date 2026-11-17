package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.FirebasePhoneAuthHelper
import com.example.auth.PhoneOtpSessionManager

@Composable
fun PhoneOtpSignInCard(
    phoneInput: String,
    otpInput: String,
    isOtpStep: Boolean,
    sentToPhoneDisplay: String,
    resendSecondsRemaining: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onPhoneInputChange: (String) -> Unit,
    onOtpInputChange: (String) -> Unit,
    onSendOtpClick: () -> Unit,
    onVerifyOtpClick: () -> Unit,
    onResendOtpClick: () -> Unit,
    onChangePhoneClick: () -> Unit,
    onGoogleFallbackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSmsProviderSetupCard: Boolean = false,
    onStartDemoNotificationOtpClick: (() -> Unit)? = null,
    demoNotificationOtpHint: String? = null,
) {
    val previewE164 = FirebasePhoneAuthHelper.formatEgyptianPhoneToE164(phoneInput)
    var revealDemoCode by rememberSaveable(sentToPhoneDisplay, demoNotificationOtpHint) {
        mutableStateOf(false)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("unauthenticated_sign_in_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Card Header + Egypt-Only Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (isOtpStep) Icons.Filled.Lock else Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.landing_sign_in_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("egypt_only_badge"),
                        ) {
                            Text(
                                text = stringResource(R.string.phone_auth_country_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.landing_sign_in_banner_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Error Alert Banner
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_auth_error_banner"),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Shown ONLY when Firebase SMS provider/billing is not yet enabled in Firebase Console
            if (showSmsProviderSetupCard && !isOtpStep) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_auth_sms_setup_card"),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "لماذا لم تصل رسالة SMS حقيقية بعد؟",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = "إرسال رسائل SMS لشبكات المحمول في مصر يتطلب تفعيل خيار Phone مع خطة Blaze في لوحة تحكم Firebase للمشروع (أو إضافة الرقم كـ Test Number في Firebase Console). يمكنك الآن الدخول بحساب Google لمزامنة الحجوزات ولوحة صاحب الصالون فوراً، أو تجربة كود إشعار تجريبي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (onStartDemoNotificationOtpClick != null) {
                            FilledTonalButton(
                                onClick = onStartDemoNotificationOtpClick,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("phone_auth_demo_notification_button"),
                            ) {
                                Text(
                                    text = "إرسال كود تجريبي في إشعار الهاتف (بدون SMS)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            if (!isOtpStep) {
                // STEP 1: Egypt-Only (+20) Mobile Number Input -> Send Real SMS OTP
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = onPhoneInputChange,
                    enabled = !isLoading,
                    singleLine = true,
                    label = { Text(stringResource(R.string.phone_auth_input_label)) },
                    placeholder = { Text(stringResource(R.string.phone_auth_input_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    prefix = {
                        Text(
                            text = "\u200E🇪🇬 +20 ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingText = {
                        val hintText = if (previewE164 != null) {
                            val localDisplay = PhoneOtpSessionManager.toLocalEgyptianDisplay(previewE164)
                            "رقم مصري صحيح: \u200E$localDisplay (\u200E$previewE164)"
                        } else {
                            "متاح داخل مصر فقط لشبكات (010 • 011 • 012 • 015)"
                        }
                        Text(hintText)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (!isLoading && phoneInput.isNotBlank()) onSendOtpClick() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_auth_number_input"),
                )

                Button(
                    onClick = onSendOtpClick,
                    enabled = !isLoading && phoneInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("phone_auth_send_otp_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.phone_auth_send_code_btn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                // STEP 2: Blank 6-Digit OTP Input (User manually enters code from SMS / notification; NEVER auto-filled!)
                AnimatedVisibility(visible = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Sms,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "اكتب الأرقام الـ 6 المرسلة إلى \u200E$sentToPhoneDisplay",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }

                                    TextButton(
                                        onClick = onChangePhoneClick,
                                        enabled = !isLoading,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("phone_auth_change_number_button"),
                                    ) {
                                        Text(stringResource(R.string.phone_auth_change_number))
                                    }
                                }

                                // Only if user explicitly chose Demo Notification mode AND notifications are hidden on emulator
                                if (!demoNotificationOtpHint.isNullOrBlank()) {
                                    if (!revealDemoCode) {
                                        TextButton(
                                            onClick = { revealDemoCode = true },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("phone_auth_reveal_demo_otp_button"),
                                        ) {
                                            Text(
                                                text = "لم يظهر إشعار التجربة أعلى المحاكي؟ اضغط لعرض الكود التجريبي",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "الكود التجريبي للإشعار: \u200E$demoNotificationOtpHint (اكتبه بالأسفل)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = onOtpInputChange,
                                enabled = !isLoading,
                                singleLine = true,
                                label = { Text(stringResource(R.string.phone_auth_otp_label)) },
                                placeholder = { Text(stringResource(R.string.phone_auth_otp_hint)) },
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 6.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (!isLoading && otpInput.length == 6) onVerifyOtpClick() },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_auth_otp_input"),
                            )
                        }

                        Button(
                            onClick = onVerifyOtpClick,
                            enabled = !isLoading && otpInput.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("phone_auth_verify_otp_button"),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.phone_auth_verify_btn),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (resendSecondsRemaining > 0) {
                                Text(
                                    text = "يمكنك إعادة إرسال رمز التحقق خلال $resendSecondsRemaining ثانية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                TextButton(
                                    onClick = onResendOtpClick,
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("phone_auth_resend_otp_button"),
                                ) {
                                    Text(stringResource(R.string.phone_auth_resend_code))
                                }
                            }
                        }
                    }
                }
            }

            // Secondary Google Sign-In Button (also unlocks Salon Owner Dashboard for fareselbialy1941@gmail.com)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
                Text(
                    text = stringResource(R.string.phone_auth_or_divider),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
            }

            OutlinedButton(
                onClick = onGoogleFallbackClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_in_button"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sign_in_google_secondary),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
