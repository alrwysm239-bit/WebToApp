package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AdminConfigRepository
import com.example.ai.GeminiConfig
import com.example.ai.GeminiRest
import com.example.ai.GoogleAuthHelper
import com.example.ai.ProxyConnectionTestResult
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

private const val CLOUDFLARE_WORKER_SAMPLE = """export default {
  async fetch(req, env) {
    const u = new URL(req.url);
    const target = "https://generativelanguage.googleapis.com" + u.pathname + u.search;
    return fetch(target, {
      method: req.method,
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": env.GEMINI_API_KEY
      },
      body: req.method === "POST" ? await req.text() : undefined
    });
  }
};"""

@Composable
fun AdminControlPanelDialog(
    onDismiss: () -> Unit,
    onNotifyMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalWebForgeColors.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val adminRepo = remember(context) { AdminConfigRepository(context.applicationContext) }

    val authUser by remember { GoogleAuthHelper.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = GoogleAuthHelper.currentUserOrNull())

    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var checkingAdmin by remember { mutableStateOf(false) }
    var isAdminUser by remember { mutableStateOf(false) }
    var passwordUnlocked by remember { mutableStateOf(false) }

    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var savedSalt by remember { mutableStateOf(GeminiConfig.getLocalAdminPasswordSalt(context)) }
    var savedHash by remember { mutableStateOf(GeminiConfig.getLocalAdminPasswordHash(context)) }

    var aiEnabled by remember { mutableStateOf(GeminiConfig.isAiEnabled(context)) }
    var proxyUrlInput by remember { mutableStateOf(GeminiConfig.getCustomProxyUrl(context)) }
    var proxyUrlError by remember { mutableStateOf<String?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    var showWorkerCode by remember { mutableStateOf(false) }
    var savingCloud by remember { mutableStateOf(false) }
    var testingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<ProxyConnectionTestResult?>(null) }
    var panelStatus by remember { mutableStateOf<String?>(null) }

    val currentUid = authUser?.uid.orEmpty()
    LaunchedEffect(currentUid) {
        passwordUnlocked = false
        passwordInput = ""
        confirmPasswordInput = ""
        passwordError = null
        proxyUrlError = null
        connectionTestResult = null
        panelStatus = null
        val user = authUser
        if (user == null || user.uid.isBlank()) {
            isAdminUser = false
            checkingAdmin = false
            return@LaunchedEffect
        }
        checkingAdmin = true
        val adminOk = adminRepo.checkIsAdmin()
        isAdminUser = adminOk
        if (adminOk) {
            adminRepo.ensureBootstrappedAdminRecord()
            adminRepo.loadAdminSettings(context.applicationContext).onSuccess { remote ->
                if (remote != null) {
                    aiEnabled = remote.aiEnabled
                    proxyUrlInput = remote.proxyUrl
                    savedSalt = remote.passwordSalt
                    savedHash = remote.passwordHash
                }
            }
        }
        checkingAdmin = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AdminPanelSettings, null, tint = ext.accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "لوحة تحكم المشرف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    authUser == null -> {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, ext.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Security, null, tint = ext.accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "الدخول محمي للمشرف فقط",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = "سجّل الدخول بحساب Google المصرّح له لإدارة الخادم الوسيط وتفعيل الذكاء الاصطناعي لجميع المستخدمين.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!authError.isNullOrBlank()) {
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Button(
                                    onClick = {
                                        authBusy = true
                                        authError = null
                                        GoogleAuthHelper.onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = { authBusy = false },
                                            onAuthError = { msg ->
                                                authBusy = false
                                                authError = msg
                                            },
                                            scope = scope,
                                            onAuthCancelled = { authBusy = false },
                                        )
                                    },
                                    enabled = !authBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("admin_google_signin_button"),
                                ) {
                                    if (authBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("تسجيل الدخول بحساب Google")
                                }
                            }
                        }
                    }

                    checkingAdmin -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ext.accent)
                            Spacer(Modifier.width(10.dp))
                            Text("جارٍ التحقق من صلاحيات المشرف...")
                        }
                    }

                    !isAdminUser -> {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "هذا الحساب (${authUser?.email.orEmpty()}) غير مصرّح له بالدخول إلى لوحة تحكم المشرف.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                OutlinedGlowButton(
                                    text = "تسجيل الخروج",
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    onClick = {
                                        GoogleAuthHelper.signOut(credentialManager, scope) {
                                            onNotifyMessage("تم تسجيل الخروج")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "admin_non_admin_signout_button",
                                )
                            }
                        }
                    }

                    !passwordUnlocked -> {
                        val hasExistingPassword = savedHash.isNotBlank() && savedSalt.isNotBlank()
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, ext.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.VerifiedUser, null, tint = ext.success, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = authUser?.email.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ext.success,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Lock, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (hasExistingPassword) {
                                            "أدخل كلمة سر لوحة التحكم"
                                        } else {
                                            "أنشئ كلمة سر للوحة التحكم (لأول مرة)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        passwordError = null
                                    },
                                    label = { Text("كلمة السر") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("admin_password_input"),
                                )
                                if (!hasExistingPassword) {
                                    OutlinedTextField(
                                        value = confirmPasswordInput,
                                        onValueChange = {
                                            confirmPasswordInput = it
                                            passwordError = null
                                        },
                                        label = { Text("تأكيد كلمة السر") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("admin_password_confirm_input"),
                                    )
                                }
                                if (!passwordError.isNullOrBlank()) {
                                    Text(
                                        text = passwordError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                GradientButton(
                                    text = if (hasExistingPassword) "فتح لوحة التحكم" else "تعيين كلمة السر وفتح اللوحة",
                                    icon = Icons.Rounded.LockOpen,
                                    enabled = !savingCloud,
                                    onClick = {
                                        if (hasExistingPassword) {
                                            if (GoogleAuthHelper.verifyAdminPassword(passwordInput, savedSalt, savedHash)) {
                                                passwordUnlocked = true
                                                passwordInput = ""
                                                passwordError = null
                                            } else {
                                                passwordError = "كلمة السر غير صحيحة."
                                            }
                                        } else {
                                            if (passwordInput.length < 6) {
                                                passwordError = "يجب أن تتكون كلمة السر من 6 أحرف على الأقل."
                                            } else if (passwordInput != confirmPasswordInput) {
                                                passwordError = "كلمتا السر غير متطابقتين."
                                            } else {
                                                val safeProxy = GeminiConfig.validateProxyUrl(proxyUrlInput)
                                                    .getOrDefault("")
                                                val newSalt = GoogleAuthHelper.generateSaltHex()
                                                val newHash = GoogleAuthHelper.hashAdminPassword(passwordInput, newSalt)
                                                savingCloud = true
                                                scope.launch {
                                                    val res = adminRepo.saveAdminSettings(
                                                        appContext = context.applicationContext,
                                                        aiEnabled = aiEnabled,
                                                        proxyUrl = safeProxy,
                                                        passwordSalt = newSalt,
                                                        passwordHash = newHash,
                                                    )
                                                    savingCloud = false
                                                    savedSalt = newSalt
                                                    savedHash = newHash
                                                    passwordUnlocked = true
                                                    passwordInput = ""
                                                    confirmPasswordInput = ""
                                                    if (res.isFailure) {
                                                        panelStatus = "تم الفتح محلياً (تعذر الحفظ السحابي حالياً)"
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "admin_unlock_button",
                                )
                                TextButton(
                                    onClick = {
                                        GoogleAuthHelper.signOut(credentialManager, scope) {
                                            onNotifyMessage("تم تسجيل الخروج")
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("تبديل الحساب", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    else -> {
                        // Unlocked Admin Panel
                        Surface(
                            color = ext.success.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ext.success.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = ext.success, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "المشرف: ${authUser?.email.orEmpty()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ext.success,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        passwordUnlocked = false
                                        GoogleAuthHelper.signOut(credentialManager, scope) {
                                            onNotifyMessage("تم قفل اللوحة وتسجيل الخروج")
                                        }
                                    },
                                ) {
                                    Text("خروج", color = ext.warning)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("تفعيل الذكاء الاصطناعي للتطبيق", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "تشغيل أو إيقاف خدمة الذكاء الاصطناعي لجميع المستخدمين",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = aiEnabled,
                                onCheckedChange = { aiEnabled = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = ext.success),
                                modifier = Modifier.testTag("admin_ai_enabled_switch"),
                            )
                        }

                        HorizontalDivider(color = ext.cardBorder)

                        // Proxy URL Configuration (DEFAULT_PROXY_URL used automatically when blank)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, tint = ext.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("رابط الخادم الوسيط (Proxy URL)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "الخادم الافتراضي يعمل تلقائياً للجميع بدون تسجيل دخول أو مفتاح على الجهاز: ${GeminiConfig.DEFAULT_PROXY_URL}. يمكنك إدخال رابط مخصص يبدأ بـ https:// لتجاوزه.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = proxyUrlInput,
                            onValueChange = {
                                proxyUrlInput = it
                                proxyUrlError = null
                            },
                            label = { Text("رابط مخصص (اختياري — يتقدّم على الافتراضي)") },
                            placeholder = { Text(GeminiConfig.DEFAULT_PROXY_URL) },
                            isError = !proxyUrlError.isNullOrBlank(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("admin_proxy_url_input"),
                        )
                        if (!proxyUrlError.isNullOrBlank()) {
                            Text(
                                text = proxyUrlError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("admin_proxy_url_error"),
                            )
                        }

                        // Test Connection Button + Result
                        OutlinedGlowButton(
                            text = if (testingConnection) "جارٍ اختبار الاتصال..." else "اختبار الاتصال",
                            icon = Icons.Rounded.NetworkCheck,
                            onClick = {
                                if (testingConnection) return@OutlinedGlowButton
                                val validation = GeminiConfig.validateProxyUrl(proxyUrlInput)
                                if (validation.isFailure) {
                                    val msg = validation.exceptionOrNull()?.localizedMessage
                                        ?: "رابط الخادم الوسيط غير صالح."
                                    proxyUrlError = msg
                                    connectionTestResult = ProxyConnectionTestResult(
                                        success = false,
                                        testedUrl = proxyUrlInput.trim(),
                                        latencyMs = 0L,
                                        message = msg,
                                    )
                                    return@OutlinedGlowButton
                                }
                                proxyUrlError = null
                                testingConnection = true
                                connectionTestResult = null
                                scope.launch {
                                    val result = GeminiRest.testConnection(proxyUrlInput)
                                    testingConnection = false
                                    connectionTestResult = result
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "admin_test_connection_button",
                        )

                        connectionTestResult?.let { testRes ->
                            Surface(
                                color = if (testRes.success) {
                                    ext.success.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (testRes.success) ext.success.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_test_connection_result"),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = testRes.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (testRes.success) ext.success else MaterialTheme.colorScheme.error,
                                    )
                                    if (testRes.testedUrl.isNotBlank()) {
                                        Text(
                                            text = testRes.testedUrl,
                                            style = TextStyle(fontFamily = MonoFontFamily, fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        TextButton(onClick = { showWorkerCode = !showWorkerCode }) {
                            Text(
                                text = if (showWorkerCode) "إخفاء كود Cloudflare Worker" else "عرض ونسخ كود Cloudflare Worker",
                                color = ext.accent,
                            )
                        }
                        if (showWorkerCode) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, ext.cardBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = CLOUDFLARE_WORKER_SAMPLE,
                                        style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp),
                                    )
                                    OutlinedGlowButton(
                                        text = "نسخ كود Worker",
                                        icon = Icons.Rounded.ContentCopy,
                                        onClick = {
                                            clipboard.setText(AnnotatedString(CLOUDFLARE_WORKER_SAMPLE))
                                            onNotifyMessage("تم نسخ كود Cloudflare Worker")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ext.cardBorder)

                        OutlinedTextField(
                            value = newPasswordInput,
                            onValueChange = { newPasswordInput = it },
                            label = { Text("تغيير كلمة سر لوحة التحكم (اختياري)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("admin_new_password_input"),
                        )

                        if (!panelStatus.isNullOrBlank()) {
                            Text(
                                text = panelStatus.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.accent,
                            )
                        }

                        GradientButton(
                            text = if (savingCloud) "جارٍ الحفظ والمزامنة..." else "حفظ ومزامنة إعدادات المشرف",
                            icon = Icons.Rounded.CloudDone,
                            enabled = !savingCloud,
                            onClick = {
                                val validation = GeminiConfig.validateProxyUrl(proxyUrlInput)
                                val validatedProxy = validation.getOrElse { err ->
                                    val msg = err.localizedMessage ?: "رابط الخادم الوسيط غير صالح."
                                    proxyUrlError = msg
                                    panelStatus = msg
                                    return@GradientButton
                                }
                                proxyUrlError = null
                                proxyUrlInput = validatedProxy

                                val nextPw = newPasswordInput.trim()
                                if (nextPw.isNotEmpty() && nextPw.length < 6) {
                                    panelStatus = "كلمة السر الجديدة يجب أن تكون 6 أحرف على الأقل."
                                    return@GradientButton
                                }
                                val finalSalt = if (nextPw.isNotEmpty()) GoogleAuthHelper.generateSaltHex() else savedSalt
                                val finalHash = if (nextPw.isNotEmpty()) GoogleAuthHelper.hashAdminPassword(nextPw, finalSalt) else savedHash

                                savingCloud = true
                                panelStatus = null
                                scope.launch {
                                    val result = adminRepo.saveAdminSettings(
                                        appContext = context.applicationContext,
                                        aiEnabled = aiEnabled,
                                        proxyUrl = validatedProxy,
                                        passwordSalt = finalSalt,
                                        passwordHash = finalHash,
                                    )
                                    savingCloud = false
                                    savedSalt = finalSalt
                                    savedHash = finalHash
                                    newPasswordInput = ""
                                    if (result.isSuccess) {
                                        panelStatus = "تم حفظ الإعدادات وتطبيقها بنجاح ✓"
                                        onNotifyMessage("تم حفظ إعدادات لوحة التحكم ✓")
                                    } else {
                                        // Still save locally if cloud write failed temporarily (e.g. offline), since URL was validated
                                        GeminiConfig.applyAdminCloudSettings(
                                            aiEnabled = aiEnabled,
                                            proxyUrl = validatedProxy,
                                            passwordSalt = finalSalt,
                                            passwordHash = finalHash,
                                            context = context.applicationContext,
                                        )
                                        panelStatus = "تم الحفظ محلياً؛ تعذر التحديث السحابي: ${result.exceptionOrNull()?.localizedMessage.orEmpty()}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "admin_save_cloud_button",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("إغلاق")
            }
        },
    )
}
