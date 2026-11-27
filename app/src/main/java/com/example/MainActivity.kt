package com.example

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiConfig
import com.example.ai.configureAppCheck
import com.example.ui.WebForgeApp
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.WebForgeTheme
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

private const val CRASH_PREFS = "webforge_crash_prefs"
private const val KEY_LAST_CRASH_TRACE = "last_crash_stacktrace"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val initialCrashReport = prefs.getString(KEY_LAST_CRASH_TRACE, null)
            ?.takeIf { it.isNotBlank() }

        // Save full stack trace synchronously and ALWAYS delegate to the original handler
        // (never swallow exceptions on main or background threads).
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("Thread: ${thread.name}")
                throwable.printStackTrace(pw)
                pw.flush()
                prefs.edit()
                    .putString(KEY_LAST_CRASH_TRACE, sw.toString().take(24_000))
                    .commit()
            } catch (_: Throwable) {
                // Ignore secondary failures inside crash logger
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }

        super.onCreate(savedInstanceState)

        try {
            configureAppCheck(this, intent)
        } catch (_: Throwable) {
            // App must continue running cleanly even without Firebase / AppCheck
        }
        try {
            GeminiConfig.init(this)
        } catch (_: Throwable) {
            // Safe fallback if storage is temporarily unavailable
        }

        enableEdgeToEdge()
        setContent {
            var crashReport by remember { mutableStateOf(initialCrashReport) }
            if (crashReport != null) {
                CrashReportScreen(
                    stackTrace = crashReport.orEmpty(),
                    onRestartApp = {
                        prefs.edit().remove(KEY_LAST_CRASH_TRACE).commit()
                        crashReport = null
                        val restartIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restartIntent)
                        finish()
                    },
                )
            } else {
                WebForgeApp()
            }
        }
    }
}

@Composable
private fun CrashReportScreen(
    stackTrace: String,
    onRestartApp: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    WebForgeTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = "تقرير الخطأ",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "تقرير الخطأ",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Text(
                        text = "تعرض التطبيق لخطأ غير متوقع في الجلسة السابقة. يمكنك نسخ التقرير أدناه وإرساله أو الضغط على إعادة التشغيل.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            SelectionContainer {
                                Text(
                                    text = stackTrace,
                                    style = TextStyle(
                                        fontFamily = MonoFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(14.dp)
                                        .testTag("crash_report_trace_text"),
                                )
                            }
                        }
                    }

                    if (copied) {
                        Text(
                            text = "تم نسخ تقرير الخطأ إلى الحافظة ✓",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(stackTrace))
                                copied = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("crash_copy_button"),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("نسخ")
                        }

                        Button(
                            onClick = onRestartApp,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("crash_restart_button"),
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة التشغيل")
                        }
                    }
                }
            }
        }
    }
}
