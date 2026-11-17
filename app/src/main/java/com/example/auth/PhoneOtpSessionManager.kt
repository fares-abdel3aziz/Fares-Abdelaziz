package com.example.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.AppointmentReminderReceiver
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VerifiedPhoneSession(
    val sessionId: String,
    val phoneE164: String,
    val localPhone: String,
)

data class OtpDispatchResult(
    val phoneE164: String,
    val localPhone: String,
    val otpCode: String,
    val notificationPosted: Boolean,
)

/**
 * Manages first-party Phone Number + 6-digit OTP verification and persistent phone sessions
 * directly inside the app (with high-priority local notification delivery) without redirecting
 * the user to Chrome or Google Sign-In.
 */
object PhoneOtpSessionManager {

    const val OTP_CHANNEL_ID = "abo_al_ata_otp_verification"
    private const val PREFS_NAME = "abo_al_ata_phone_auth_prefs"
    private const val KEY_VERIFIED_E164 = "verified_phone_e164"
    private const val KEY_VERIFIED_LOCAL = "verified_phone_local"

    private const val OTP_VALIDITY_MS = 5L * 60L * 1000L // 5 minutes
    private const val OTP_NOTIFICATION_ID = 7071

    private val secureRandom = SecureRandom()

    private data class PendingOtpChallenge(
        val phoneE164: String,
        val localPhone: String,
        val code: String,
        val expiresAtMillis: Long,
    )

    @Volatile
    private var pendingChallenge: PendingOtpChallenge? = null

    @Volatile
    private var initialized = false

    private val _sessionFlow = MutableStateFlow<VerifiedPhoneSession?>(null)

    fun observeSession(context: Context): StateFlow<VerifiedPhoneSession?> {
        ensureInitialized(context.applicationContext)
        return _sessionFlow.asStateFlow()
    }

    fun currentSession(context: Context): VerifiedPhoneSession? {
        ensureInitialized(context.applicationContext)
        return _sessionFlow.value
    }

    private fun ensureInitialized(appContext: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val savedE164 = prefs.getString(KEY_VERIFIED_E164, null)
                    val savedLocal = prefs.getString(KEY_VERIFIED_LOCAL, null)
                    if (!savedE164.isNullOrBlank()) {
                        _sessionFlow.value = VerifiedPhoneSession(
                            sessionId = buildSessionId(savedE164),
                            phoneE164 = savedE164,
                            localPhone = savedLocal?.takeIf { it.isNotBlank() } ?: toLocalEgyptianDisplay(savedE164),
                        )
                    }
                    initialized = true
                }
            }
        }
    }

    fun ensureOtpNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OTP_CHANNEL_ID,
                "رموز التحقق لتسجيل الدخول",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "إشعارات فورية تحتوي على رمز التحقق المكوّن من 6 أرقام لتأكيد رقم الهاتف"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Generates a fresh 6-digit verification code for [rawPhone] and dispatches it immediately
     * as a high-priority heads-up notification on the user's phone (never opening Chrome or Google).
     */
    fun sendOtpCode(
        context: Context,
        rawPhone: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<OtpDispatchResult> {
        val appContext = context.applicationContext
        val e164 = AuthManager.normalizePhoneToE164(rawPhone)
            ?: return Result.failure(
                IllegalArgumentException("يرجى إدخال رقم هاتف صحيح (مثال: 01029898309)")
            )

        val localPhone = toLocalEgyptianDisplay(e164)
        val otpNumber = 100_000 + secureRandom.nextInt(900_000)
        val otpCode = otpNumber.toString()

        pendingChallenge = PendingOtpChallenge(
            phoneE164 = e164,
            localPhone = localPhone,
            code = otpCode,
            expiresAtMillis = nowMillis + OTP_VALIDITY_MS,
        )

        val notificationPosted = postOtpNotification(
            context = appContext,
            localPhone = localPhone,
            otpCode = otpCode,
        )

        return Result.success(
            OtpDispatchResult(
                phoneE164 = e164,
                localPhone = localPhone,
                otpCode = otpCode,
                notificationPosted = notificationPosted,
            )
        )
    }

    /**
     * Validates the 6-digit OTP entered by the customer and establishes an authenticated
     * phone session persisted across app restarts.
     */
    fun verifyOtpCode(
        context: Context,
        rawOtpCode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<VerifiedPhoneSession> {
        val appContext = context.applicationContext
        ensureInitialized(appContext)

        val digits = AuthManager.normalizeOtpDigits(rawOtpCode)
        if (digits.length != 6) {
            return Result.failure(
                IllegalArgumentException("يرجى إدخال رمز التحقق المكوّن من 6 أرقام كاملاً")
            )
        }

        val challenge = pendingChallenge
            ?: return Result.failure(
                IllegalStateException("انتهت جلسة التحقق، اضغط إعادة إرسال الرمز للحصول على كود جديد")
            )

        if (nowMillis > challenge.expiresAtMillis) {
            pendingChallenge = null
            return Result.failure(
                IllegalStateException("انتهت صلاحية رمز التحقق، يرجى طلب إرسال رمز جديد")
            )
        }

        if (digits != challenge.code) {
            return Result.failure(
                IllegalArgumentException("رمز التحقق غير صحيح، يرجى التأكد من الأرقام الـ 6 الصحيحة")
            )
        }

        pendingChallenge = null
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(OTP_NOTIFICATION_ID)
        }

        val session = VerifiedPhoneSession(
            sessionId = buildSessionId(challenge.phoneE164),
            phoneE164 = challenge.phoneE164,
            localPhone = challenge.localPhone,
        )

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VERIFIED_E164, session.phoneE164)
            .putString(KEY_VERIFIED_LOCAL, session.localPhone)
            .apply()

        _sessionFlow.value = session
        return Result.success(session)
    }

    fun clearSession(context: Context) {
        val appContext = context.applicationContext
        pendingChallenge = null
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VERIFIED_E164)
            .remove(KEY_VERIFIED_LOCAL)
            .apply()
        _sessionFlow.value = null
    }

    fun toLocalEgyptianDisplay(e164: String): String {
        return if (e164.startsWith("+20") && e164.length == 13) {
            "0${e164.drop(3)}"
        } else {
            e164
        }
    }

    private fun buildSessionId(phoneE164: String): String {
        val digits = phoneE164.filter { it.isDigit() }
        return "phone_$digits"
    }

    private fun postOtpNotification(
        context: Context,
        localPhone: String,
        otpCode: String,
    ): Boolean {
        ensureOtpNotificationChannel(context)
        if (!AppointmentReminderReceiver.hasNotificationPermission(context)) {
            return false
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            OTP_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = "رمز التحقق: $otpCode • صالون أبو العطا"
        val body = "كود تفعيل رقم هاتفك ($localPhone) في صالون أبو العطا هو: $otpCode — أدخل الأرقام الـ 6 داخل التطبيق لإتمام الدخول."

        val notification = NotificationCompat.Builder(context, OTP_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(OTP_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
