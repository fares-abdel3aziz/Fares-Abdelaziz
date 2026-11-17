package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

object AuthManager {

    private const val TAG = "Auth"
    const val EGYPT_DIAL_CODE = "+20"
    private val VALID_EGYPT_MOBILE_REGEX = Regex("^\\+201[0125]\\d{8}$")

    /**
     * Converts Arabic-Indic numerals (`٠-٩` / `۰-۹`) to ASCII digits and strictly validates &
     * normalizes **Egyptian mobile numbers ONLY** (`010`, `011`, `012`, `015`) into E.164 (`+201XXXXXXXXX`).
     * Rejects non-Egyptian country codes and invalid carrier prefixes.
     */
    fun normalizePhoneToE164(rawInput: String): String? {
        val normalizedChars = buildString {
            rawInput.trim().forEach { ch ->
                when (ch) {
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    in '0'..'9', '+' -> append(ch)
                }
            }
        }
        if (normalizedChars.isBlank()) return null

        val hasLeadingPlus = normalizedChars.startsWith("+")
        val digits = normalizedChars.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val candidateE164 = when {
            // Explicit international prefix (+201XXXXXXXXX or +2001XXXXXXXXX)
            hasLeadingPlus -> {
                if (digits.startsWith("2001") && digits.length == 13) {
                    "+20${digits.drop(3)}"
                } else {
                    "+$digits"
                }
            }
            // 0020 international prefix (00201XXXXXXXXX or 002001XXXXXXXXX)
            digits.startsWith("00") -> {
                val afterZeroes = digits.drop(2)
                if (afterZeroes.startsWith("2001") && afterZeroes.length == 13) {
                    "+20${afterZeroes.drop(3)}"
                } else {
                    "+$afterZeroes"
                }
            }
            // Standard 11-digit Egyptian mobile number (010 / 011 / 012 / 015 + 8 digits)
            digits.length == 11 && digits.startsWith("01") -> "+2$digits"
            // 10-digit Egyptian mobile number without leading trunk '0' (10 / 11 / 12 / 15 + 8 digits)
            digits.length == 10 && digits.startsWith("1") -> "+20$digits"
            // 12-digit Egyptian number starting with country code 20 (201XXXXXXXXX)
            digits.length == 12 && digits.startsWith("201") -> "+$digits"
            else -> return null
        }

        return candidateE164.takeIf { VALID_EGYPT_MOBILE_REGEX.matches(it) }
    }

    /**
     * Normalizes an OTP verification code (supporting Arabic-Indic digits) to 6 ASCII digits.
     */
    fun normalizeOtpDigits(rawCode: String): String {
        return buildString {
            rawCode.forEach { ch ->
                when (ch) {
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    in '0'..'9' -> append(ch)
                }
            }
        }.take(6)
    }

    /**
     * Secondary manual Google Sign-In option via Jetpack Credential Manager (`GetSignInWithGoogleOption`).
     * Only invoked when the user explicitly clicks the secondary Google Sign-In button.
     */
    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {},
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("إعدادات تسجيل الدخول بحساب Google غير مكتملة")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context.findHostActivity() ?: run {
                    onAuthError("تعذر فتح نافذة تسجيل الدخول")
                    return@launch
                }
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onAuthError("نوع بيانات الاعتماد غير متوقع")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "تعذر تسجيل الدخول بحساب Google")
            }
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {},
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    PhoneOtpSessionManager.clearSession(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
