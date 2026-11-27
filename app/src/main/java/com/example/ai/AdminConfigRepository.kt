package com.example.ai

import android.content.Context
import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class AdminAiSettings(
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val aiEnabled: Boolean = true,
    val proxyUrl: String = "",
    val geminiApiKey: String = "",
    val defaultModel: String = GeminiConfig.MODEL,
    val passwordSalt: String = "",
    val passwordHash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PublicAiRuntimeConfig(
    val aiEnabled: Boolean = true,
    val proxyUrl: String = "",
    val defaultModel: String = GeminiConfig.MODEL,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

class AdminConfigRepository private constructor(
    private val dbProvider: () -> FirebaseFirestore?,
    private val authProvider: () -> FirebaseAuth?,
) {
    constructor(
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth,
    ) : this(
        dbProvider = { db },
        authProvider = { auth },
    )

    /**
     * Lazy, exception-safe constructor so creating AdminConfigRepository in Compose never crashes
     * even when Firebase/google-services.json is absent.
     */
    constructor(context: Context) : this(
        dbProvider = {
            runCatching {
                val appCtx = context.applicationContext ?: context
                val resId = appCtx.resources.getIdentifier("firestore_database_id", "string", appCtx.packageName)
                val dbId = if (resId != 0) appCtx.getString(resId).trim() else ""
                if (dbId.isNotBlank()) {
                    FirebaseFirestore.getInstance(dbId)
                } else {
                    null
                }
            }.getOrNull()
        },
        authProvider = {
            runCatching { Firebase.auth }.getOrNull()
        },
    )

    private fun requireFirebaseReady(): Pair<FirebaseFirestore, FirebaseAuth> {
        val db = dbProvider()
            ?: throw IllegalStateException("خدمة Firestore غير مهيأة في هذه النسخة (يعمل الذكاء الاصطناعي عبر الخادم الوسيط تلقائياً).")
        val auth = authProvider()
            ?: throw IllegalStateException("خدمة Firebase Auth غير مهيأة في هذه النسخة.")
        return db to auth
    }

    private fun requireUserId(auth: FirebaseAuth): String {
        return auth.currentUser?.uid
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً.")
    }

    suspend fun checkIsAdmin(): Boolean {
        val (db, auth) = runCatching { requireFirebaseReady() }.getOrNull() ?: return false
        val user = runCatching { auth.currentUser }.getOrNull() ?: return false
        if (GoogleAuthHelper.isBootstrappedAdmin(user)) return true
        val uid = user.uid
        if (uid.isBlank()) return false
        val path = "admins/$uid"
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    suspend fun ensureBootstrappedAdminRecord(): Result<Unit> {
        return try {
            val (db, auth) = requireFirebaseReady()
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
            if (!GoogleAuthHelper.isBootstrappedAdmin(user)) return Result.success(Unit)
            val uid = user.uid
            val path = "admins/$uid"
            try {
                val docRef = db.collection("admins").document(uid)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    val payload = mapOf(
                        "uid" to uid,
                        "email" to (user.email ?: GoogleAuthHelper.BOOTSTRAPPED_ADMIN_EMAIL),
                        "displayName" to (user.displayName ?: "Admin").take(120),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    docRef.set(payload).await()
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                handleFirestoreError(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun loadAdminSettings(appContext: Context): Result<AdminAiSettings?> {
        val path = "admin_settings/gemini"
        return try {
            val (db, auth) = requireFirebaseReady()
            requireUserId(auth)
            val snap = db.collection("admin_settings").document("gemini").get().await()
            val settings = if (snap.exists()) {
                snap.toObject(AdminAiSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            if (settings != null) {
                GeminiConfig.applyAdminCloudSettings(
                    aiEnabled = settings.aiEnabled,
                    proxyUrl = settings.proxyUrl,
                    passwordSalt = settings.passwordSalt,
                    passwordHash = settings.passwordHash,
                    context = appContext,
                )
            }
            Result.success(settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveAdminSettings(
        appContext: Context,
        aiEnabled: Boolean,
        proxyUrl: String,
        passwordSalt: String,
        passwordHash: String,
        defaultModel: String = GeminiConfig.MODEL,
    ): Result<Unit> {
        val adminPath = "admin_settings/gemini"
        val publicPath = "public_ai_config/runtime"
        val cleanProxy = GeminiConfig.validateProxyUrl(proxyUrl)
            .getOrElse { return Result.failure(it) }
            .take(500)
        return try {
            val (db, auth) = requireFirebaseReady()
            val uid = requireUserId(auth)
            val user = auth.currentUser!!
            val cleanModel = defaultModel.trim().ifBlank { GeminiConfig.MODEL }.take(80)
            val cleanSalt = passwordSalt.trim().take(128)
            val cleanHash = passwordHash.trim().take(128)

            val adminRef = db.collection("admin_settings").document("gemini")
            val adminExists = adminRef.get().await().exists()
            if (!adminExists) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "ownerEmail" to (user.email ?: GoogleAuthHelper.BOOTSTRAPPED_ADMIN_EMAIL).take(200),
                    "aiEnabled" to aiEnabled,
                    "proxyUrl" to cleanProxy,
                    "geminiApiKey" to "",
                    "defaultModel" to cleanModel,
                    "passwordSalt" to cleanSalt,
                    "passwordHash" to cleanHash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                adminRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "aiEnabled" to aiEnabled,
                    "proxyUrl" to cleanProxy,
                    "geminiApiKey" to "",
                    "defaultModel" to cleanModel,
                    "passwordSalt" to cleanSalt,
                    "passwordHash" to cleanHash,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                adminRef.update(updatePayload).await()
            }

            val publicRef = db.collection("public_ai_config").document("runtime")
            val publicExists = publicRef.get().await().exists()
            if (!publicExists) {
                val createPublic = mapOf(
                    "aiEnabled" to aiEnabled,
                    "proxyUrl" to cleanProxy,
                    "defaultModel" to cleanModel,
                    "updatedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                publicRef.set(createPublic).await()
            } else {
                val updatePublic = mapOf(
                    "aiEnabled" to aiEnabled,
                    "proxyUrl" to cleanProxy,
                    "defaultModel" to cleanModel,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                publicRef.update(updatePublic).await()
            }

            GeminiConfig.applyAdminCloudSettings(
                aiEnabled = aiEnabled,
                proxyUrl = cleanProxy,
                passwordSalt = cleanSalt,
                passwordHash = cleanHash,
                context = appContext,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.WRITE, "$adminPath / $publicPath")
            Result.failure(e)
        }
    }

    suspend fun syncSignedInPublicRuntime(appContext: Context): Result<PublicAiRuntimeConfig?> {
        val (db, auth) = runCatching { requireFirebaseReady() }.getOrNull() ?: return Result.success(null)
        if (runCatching { auth.currentUser?.uid }.getOrNull().isNullOrBlank()) return Result.success(null)
        val path = "public_ai_config/runtime"
        return try {
            val snap = db.collection("public_ai_config").document("runtime").get().await()
            val runtime = if (snap.exists()) {
                snap.toObject(PublicAiRuntimeConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            if (runtime != null) {
                GeminiConfig.applyPublicRuntime(
                    aiEnabled = runtime.aiEnabled,
                    proxyUrl = runtime.proxyUrl,
                    context = appContext,
                )
            }
            Result.success(runtime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}
