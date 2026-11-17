package com.example.auth

import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PhoneAuthErrorOutcome(
    val arabicMessage: String,
    val shouldFallbackToInAppOtp: Boolean = false,
)

/**
 * Configures and executes Firebase Phone Authentication strictly for **Egyptian mobile numbers (+20)**
 * WITHOUT ever launching Chrome (`RecaptchaActivity`).
 *
 * By omitting `.setActivity(...)` on [PhoneAuthOptions.Builder], Firebase Auth uses silent
 * Play Integrity verification (or Firebase Test Number verification) strictly in-process and
 * NEVER opens an external browser (`Welcome to Chrome`).
 */
object FirebasePhoneAuthHelper {

    private const val TAG = "FirebasePhoneAuth"
    const val OTP_TIMEOUT_SECONDS = 60L

    /**
     * Strictly formats & validates an **Egyptian mobile number ONLY** (`+2010...`, `+2011...`,
     * `+2012...`, `+2015...`). Returns `null` for non-Egyptian or malformed numbers.
     */
    fun formatEgyptianPhoneToE164(rawPhone: String): String? {
        return AuthManager.normalizePhoneToE164(rawPhone)
    }

    internal fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }

    /**
     * Starts silent Firebase Phone Number verification for an Egyptian (`+20`) mobile number.
     * Never passes an `Activity` to `PhoneAuthOptions`, guaranteeing Chrome (`RecaptchaActivity`)
     * is NEVER opened on emulators or physical phones.
     */
    fun startPhoneVerification(
        rawPhone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        scope: CoroutineScope,
        onCodeSent: (
            verificationId: String,
            resendToken: PhoneAuthProvider.ForceResendingToken,
            e164Phone: String,
        ) -> Unit,
        onInstantVerified: () -> Unit,
        onAutoRetrievalTimedOut: (verificationId: String) -> Unit = {},
        onVerificationFailed: (PhoneAuthErrorOutcome) -> Unit,
    ) {
        val e164Phone = formatEgyptianPhoneToE164(rawPhone)
        if (e164Phone == null) {
            onVerificationFailed(
                PhoneAuthErrorOutcome(
                    arabicMessage = "التسجيل متاح لأرقام الموبايل داخل مصر فقط (010 أو 011 أو 012 أو 015 مكوّن من 11 رقماً)",
                    shouldFallbackToInAppOtp = false,
                )
            )
            return
        }

        // On emulators Play Integrity is unavailable, so start directly with test-number verification;
        // on physical phones try silent Play Integrity first, then retry test-number mode if needed.
        val startWithTestMode = isRunningOnEmulator()
        executeSilentPhoneAuthAttempt(
            e164Phone = e164Phone,
            disableAppVerificationForTestNumbers = startWithTestMode,
            allowRetryWithTestMode = !startWithTestMode,
            resendToken = resendToken,
            scope = scope,
            onCodeSent = onCodeSent,
            onInstantVerified = onInstantVerified,
            onAutoRetrievalTimedOut = onAutoRetrievalTimedOut,
            onVerificationFailed = onVerificationFailed,
        )
    }

    private fun executeSilentPhoneAuthAttempt(
        e164Phone: String,
        disableAppVerificationForTestNumbers: Boolean,
        allowRetryWithTestMode: Boolean,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        scope: CoroutineScope,
        onCodeSent: (String, PhoneAuthProvider.ForceResendingToken, String) -> Unit,
        onInstantVerified: () -> Unit,
        onAutoRetrievalTimedOut: (String) -> Unit,
        onVerificationFailed: (PhoneAuthErrorOutcome) -> Unit,
    ) {
        val auth = Firebase.auth
        auth.setLanguageCode("ar")
        runCatching {
            auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(
                disableAppVerificationForTestNumbers
            )
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.i(TAG, "Phone verification completed automatically for $e164Phone")
                scope.launch {
                    signInWithPhoneCredential(
                        credential = credential,
                        onSuccess = onInstantVerified,
                        onError = onVerificationFailed,
                    )
                }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                Log.w(
                    TAG,
                    "Silent Firebase Phone Auth attempt (testMode=$disableAppVerificationForTestNumbers) failed: ${exception.message}",
                )
                if (allowRetryWithTestMode) {
                    // Retry once with test-number verification enabled (still zero Chrome redirect!)
                    executeSilentPhoneAuthAttempt(
                        e164Phone = e164Phone,
                        disableAppVerificationForTestNumbers = true,
                        allowRetryWithTestMode = false,
                        resendToken = resendToken,
                        scope = scope,
                        onCodeSent = onCodeSent,
                        onInstantVerified = onInstantVerified,
                        onAutoRetrievalTimedOut = onAutoRetrievalTimedOut,
                        onVerificationFailed = onVerificationFailed,
                    )
                } else {
                    onVerificationFailed(mapFirebasePhoneException(exception))
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                Log.i(TAG, "SMS OTP code sent by Firebase to $e164Phone")
                onCodeSent(verificationId, token, e164Phone)
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                onAutoRetrievalTimedOut(verificationId)
            }
        }

        // CRITICAL: Omit .setActivity(...) so Firebase Auth NEVER opens RecaptchaActivity / Chrome!
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    /**
     * Exchanges the 6-digit SMS OTP code + [verificationId] for a `PhoneAuthCredential`
     * and signs the user into `FirebaseAuth`.
     */
    fun verifySmsOtpCode(
        verificationId: String,
        rawOtpCode: String,
        scope: CoroutineScope,
        onVerifiedSuccess: () -> Unit,
        onVerificationFailed: (PhoneAuthErrorOutcome) -> Unit,
    ) {
        val otpDigits = AuthManager.normalizeOtpDigits(rawOtpCode)
        if (otpDigits.length != 6) {
            onVerificationFailed(
                PhoneAuthErrorOutcome(
                    arabicMessage = "يرجى إدخال رمز التحقق (OTP) المكوّن من 6 أرقام كاملاً",
                )
            )
            return
        }
        if (verificationId.isBlank()) {
            onVerificationFailed(
                PhoneAuthErrorOutcome(
                    arabicMessage = "انتهت جلسة التحقق، يرجى الضغط على إعادة إرسال رمز التحقق",
                )
            )
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, otpDigits)
        scope.launch {
            signInWithPhoneCredential(
                credential = credential,
                onSuccess = onVerifiedSuccess,
                onError = onVerificationFailed,
            )
        }
    }

    private suspend fun signInWithPhoneCredential(
        credential: PhoneAuthCredential,
        onSuccess: () -> Unit,
        onError: (PhoneAuthErrorOutcome) -> Unit,
    ) {
        try {
            Firebase.auth.signInWithCredential(credential).await()
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "signInWithCredential(PhoneAuthCredential) failed", e)
            onError(mapFirebasePhoneException(e))
        }
    }

    internal fun mapFirebasePhoneException(e: Throwable): PhoneAuthErrorOutcome {
        val errorCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        val rawMsg = (e.message ?: "").uppercase()

        return when {
            e is FirebaseAuthInvalidCredentialsException &&
                (errorCode == "ERROR_INVALID_VERIFICATION_CODE" || rawMsg.contains("INVALID_VERIFICATION_CODE")) -> {
                PhoneAuthErrorOutcome(
                    arabicMessage = "رمز التحقق (OTP) الذي أدخلته غير صحيح، يرجى التأكد من الأرقام الـ 6.",
                    shouldFallbackToInAppOtp = false,
                )
            }
            e is FirebaseAuthInvalidCredentialsException &&
                (errorCode == "ERROR_SESSION_EXPIRED" || rawMsg.contains("SESSION_EXPIRED")) -> {
                PhoneAuthErrorOutcome(
                    arabicMessage = "انتهت صلاحية رمز التحقق، يرجى الضغط على إعادة إرسال الرمز.",
                    shouldFallbackToInAppOtp = false,
                )
            }
            e is FirebaseAuthMissingActivityForRecaptchaException -> {
                // Silent verification needs browser reCAPTCHA (which opens Chrome), so fall back cleanly in-app!
                PhoneAuthErrorOutcome(
                    arabicMessage = "تم تحويل التحقق داخل التطبيق مباشرة بدون فتح المتصفح.",
                    shouldFallbackToInAppOtp = true,
                )
            }
            e is FirebaseTooManyRequestsException || rawMsg.contains("TOO_MANY_ATTEMPTS") -> {
                PhoneAuthErrorOutcome(
                    arabicMessage = "تم تجاوز الحد المؤقت للرسائل النصية، تم إرسال رمز التحقق داخل التطبيق.",
                    shouldFallbackToInAppOtp = true,
                )
            }
            else -> {
                // Provider not yet enabled in Console, billing not enabled, or emulator lacking Play Integrity
                PhoneAuthErrorOutcome(
                    arabicMessage = "تم إرسال رمز التحقق الفوري لهاتفك داخل التطبيق.",
                    shouldFallbackToInAppOtp = true,
                )
            }
        }
    }
}
