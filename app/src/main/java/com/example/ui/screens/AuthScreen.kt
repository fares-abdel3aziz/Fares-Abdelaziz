package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.example.auth.AuthManager
import com.example.auth.FirebasePhoneAuthHelper
import com.example.auth.PhoneOtpSessionManager
import com.example.data.model.SalonCatalog
import com.example.notifications.AppointmentReminderReceiver
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val homeGridState = rememberLazyGridState()

    var phoneInput by rememberSaveable { mutableStateOf("") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var isOtpStep by rememberSaveable { mutableStateOf(false) }
    var sentToPhoneDisplay by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendSecondsRemaining by rememberSaveable { mutableIntStateOf(0) }

    var showSmsProviderSetupCard by rememberSaveable { mutableStateOf(false) }
    var isDemoNotificationOtpMode by rememberSaveable { mutableStateOf(false) }
    var demoNotificationOtpHint by rememberSaveable { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var browsingFullCatalog by rememberSaveable { mutableStateOf(false) }
    var browsingAdhkar by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(resendSecondsRemaining) {
        if (resendSecondsRemaining > 0) {
            delay(1_000L)
            resendSecondsRemaining -= 1
        }
    }

    // Primary Flow: Real Firebase SMS OTP (NEVER auto-fills OTP, NEVER opens Chrome!)
    val requestFirebaseSmsOtp: (Boolean) -> Unit = { isResend ->
        if (!isLoading) {
            val formattedE164 = FirebasePhoneAuthHelper.formatEgyptianPhoneToE164(phoneInput)
            if (formattedE164 == null) {
                errorMessage = "التسجيل متاح لأرقام الموبايل داخل مصر فقط (010 أو 011 أو 012 أو 015 مكوّن من 11 رقماً)"
            } else {
                isLoading = true
                errorMessage = null
                showSmsProviderSetupCard = false
                isDemoNotificationOtpMode = false
                demoNotificationOtpHint = null

                FirebasePhoneAuthHelper.startPhoneVerification(
                    rawPhone = formattedE164,
                    resendToken = if (isResend) resendToken else null,
                    scope = scope,
                    onCodeSent = { verId, token, e164 ->
                        isLoading = false
                        verificationId = verId
                        resendToken = token
                        sentToPhoneDisplay = PhoneOtpSessionManager.toLocalEgyptianDisplay(e164)
                        otpInput = "" // Always blank — user types SMS code manually!
                        isOtpStep = true
                        resendSecondsRemaining = FirebasePhoneAuthHelper.OTP_TIMEOUT_SECONDS.toInt()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "تم إرسال رسالة SMS برمز التحقق إلى رقمك ${PhoneOtpSessionManager.toLocalEgyptianDisplay(e164)}"
                            )
                        }
                    },
                    onInstantVerified = {
                        isLoading = false
                        onAuthSuccess()
                    },
                    onAutoRetrievalTimedOut = { verId ->
                        if (verificationId.isBlank()) {
                            verificationId = verId
                        }
                    },
                    onVerificationFailed = { outcome ->
                        isLoading = false
                        if (outcome.shouldFallbackToInAppOtp) {
                            // Do NOT auto-fill a fake SMS! Explain clearly & offer explicit choices.
                            showSmsProviderSetupCard = true
                        } else {
                            errorMessage = outcome.arabicMessage
                        }
                    },
                )
            }
        }
    }

    // Explicit Demo Notification Mode (ONLY if user clicks the explicit demo button while Console SMS is disabled)
    val dispatchExplicitDemoNotificationOtp: () -> Unit = {
        val formattedE164 = FirebasePhoneAuthHelper.formatEgyptianPhoneToE164(phoneInput)
        if (formattedE164 == null) {
            errorMessage = "التسجيل متاح لأرقام الموبايل داخل مصر فقط (010 / 011 / 012 / 015)"
        } else {
            val result = PhoneOtpSessionManager.sendOtpCode(context, formattedE164)
            result.fold(
                onSuccess = { dispatch ->
                    isDemoNotificationOtpMode = true
                    showSmsProviderSetupCard = false
                    isOtpStep = true
                    sentToPhoneDisplay = dispatch.localPhone
                    demoNotificationOtpHint = if (dispatch.notificationPosted) null else dispatch.otpCode
                    otpInput = "" // Keep OTP input completely empty for manual entry!
                    resendSecondsRemaining = 30
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "تم إرسال إشعار تجريبي أعلى شاشة الهاتف يحتوي على الكود المكوّن من 6 أرقام"
                        )
                    }
                },
                onFailure = { err ->
                    errorMessage = err.localizedMessage ?: "تعذر إرسال الكود التجريبي"
                },
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        dispatchExplicitDemoNotificationOtp()
    }

    val onStartExplicitDemoOtp: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppointmentReminderReceiver.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            dispatchExplicitDemoNotificationOtp()
        }
    }

    val verifyOtpCode: () -> Unit = {
        if (!isLoading) {
            errorMessage = null
            if (isDemoNotificationOtpMode) {
                isLoading = true
                val verification = PhoneOtpSessionManager.verifyOtpCode(context, otpInput)
                isLoading = false
                verification.fold(
                    onSuccess = { onAuthSuccess() },
                    onFailure = { err ->
                        errorMessage = err.localizedMessage ?: "رمز التحقق غير صحيح"
                    },
                )
            } else {
                isLoading = true
                FirebasePhoneAuthHelper.verifySmsOtpCode(
                    verificationId = verificationId,
                    rawOtpCode = otpInput,
                    scope = scope,
                    onVerifiedSuccess = {
                        isLoading = false
                        onAuthSuccess()
                    },
                    onVerificationFailed = { outcome ->
                        isLoading = false
                        errorMessage = outcome.arabicMessage
                    },
                )
            }
        }
    }

    val triggerGoogleSecondarySignIn: () -> Unit = {
        if (!isLoading) {
            isLoading = true
            errorMessage = null
            AuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                },
            )
        }
    }

    val promptPhoneSignInForBooking: () -> Unit = {
        browsingFullCatalog = false
        browsingAdhkar = false
        scope.launch {
            homeGridState.animateScrollToItem(0)
            if (phoneInput.isNotBlank() && !isOtpStep) {
                requestFirebaseSmsOtp(false)
            } else {
                snackbarHostState.showSnackbar(
                    "سجّل الدخول بالأعلى برقم هاتفك المصري أو حساب Google لإتمام الحجز"
                )
            }
        }
    }

    val barbers = remember { SalonCatalog.getBarbers(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            browsingAdhkar -> {
                AdhkarScreen(
                    onNavigateBackHome = { browsingAdhkar = false },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            browsingFullCatalog -> {
                CatalogScreen(
                    onBookHaircut = { promptPhoneSignInForBooking() },
                    onNavigateBackHome = { browsingFullCatalog = false },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                HomeScreen(
                    barbers = barbers,
                    salonPhone = SalonCatalog.DEFAULT_SALON_PHONE,
                    upcomingAppointment = null,
                    onSelectBarberAndBook = { promptPhoneSignInForBooking() },
                    onSelectPackageAndBook = { promptPhoneSignInForBooking() },
                    onSelectCutAndBook = { promptPhoneSignInForBooking() },
                    onOpenCatalog = { browsingFullCatalog = true },
                    onOpenAppointments = { promptPhoneSignInForBooking() },
                    onOpenAdhkar = { browsingAdhkar = true },
                    modifier = Modifier.padding(innerPadding),
                    gridState = homeGridState,
                    topAuthSlot = {
                        PhoneOtpSignInCard(
                            phoneInput = phoneInput,
                            otpInput = otpInput,
                            isOtpStep = isOtpStep,
                            sentToPhoneDisplay = sentToPhoneDisplay,
                            resendSecondsRemaining = resendSecondsRemaining,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            showSmsProviderSetupCard = showSmsProviderSetupCard,
                            onStartDemoNotificationOtpClick = onStartExplicitDemoOtp,
                            demoNotificationOtpHint = demoNotificationOtpHint,
                            onPhoneInputChange = { raw ->
                                phoneInput = raw.filter { ch ->
                                    ch.isDigit() || ch in '٠'..'٩' || ch == '+' || ch == ' '
                                }.take(16)
                                errorMessage = null
                                showSmsProviderSetupCard = false
                            },
                            onOtpInputChange = { raw ->
                                otpInput = AuthManager.normalizeOtpDigits(raw)
                                errorMessage = null
                            },
                            onSendOtpClick = { requestFirebaseSmsOtp(false) },
                            onVerifyOtpClick = verifyOtpCode,
                            onResendOtpClick = {
                                if (isDemoNotificationOtpMode) {
                                    onStartExplicitDemoOtp()
                                } else {
                                    requestFirebaseSmsOtp(true)
                                }
                            },
                            onChangePhoneClick = {
                                isOtpStep = false
                                otpInput = ""
                                verificationId = ""
                                demoNotificationOtpHint = null
                                isDemoNotificationOtpMode = false
                                errorMessage = null
                            },
                            onGoogleFallbackClick = triggerGoogleSecondarySignIn,
                        )
                    },
                )
            }
        }
    }
}
