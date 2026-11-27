package com.example.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

object GoogleAuthHelper {
    const val BOOTSTRAPPED_ADMIN_EMAIL = "alrwysm239@gmail.com"
    private const val TAG = "Auth"

    private fun webClientId(context: Context): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) {
            throw IllegalStateException("إعداد تسجيل الدخول بحساب Google غير متوفر (default_web_client_id غير موجود).")
        }
        return context.getString(id).also {
            if (it.isBlank()) throw IllegalStateException("قيمة default_web_client_id فارغة.")
        }
    }

    fun isBootstrappedAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return runCatching {
            user.isEmailVerified &&
                user.email?.trim()?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true
        }.getOrDefault(false)
    }

    fun currentUserOrNull(): FirebaseUser? = runCatching { Firebase.auth.currentUser }.getOrNull()

    fun authStateFlow(auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()): Flow<FirebaseUser?> = callbackFlow {
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(runCatching { firebaseAuth.currentUser }.getOrNull())
        }
        try {
            auth.addAuthStateListener(listener)
        } catch (_: Throwable) {
            trySend(null)
            close()
            return@callbackFlow
        }
        awaitClose {
            runCatching { auth.removeAuthStateListener(listener) }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager?,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope,
    ) {
        if (currentUserOrNull() != null) {
            onAuthSuccess()
            return
        }
        val auth = runCatching { Firebase.auth }.getOrNull()
        if (credentialManager == null || auth == null) {
            onUnauthenticated()
            return
        }
        val clientId = try {
            webClientId(context)
        } catch (_: Throwable) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val activity = context.findActivity() ?: context
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager?,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {},
    ) {
        val auth = runCatching { Firebase.auth }.getOrNull()
        if (auth == null) {
            onAuthError("خدمة Firebase غير مهيأة في هذه النسخة. يعمل الذكاء الاصطناعي عبر الخادم الوسيط تلقائياً بدون تسجيل دخول.")
            return
        }
        if (credentialManager == null) {
            onAuthError("مدير تسجيل الدخول غير متوفر على هذا الجهاز.")
            return
        }
        val clientId = try {
            webClientId(context)
        } catch (e: Throwable) {
            onAuthError(e.localizedMessage ?: "إعداد تسجيل الدخول بحساب Google غير مكتمل.")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("تعذر فتح نافذة تسجيل الدخول من هذا السياق.")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("نوع بيانات الاعتماد غير متوقع.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Throwable) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "فشل تسجيل الدخول بحساب Google.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager?,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {},
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager?.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    onSignOutComplete()
                }
            }
        }
    }

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashAdminPassword(password: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$password".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    fun verifyAdminPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        if (saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val actual = hashAdminPassword(password, saltHex).toByteArray(Charsets.UTF_8)
        val expected = expectedHashHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}
