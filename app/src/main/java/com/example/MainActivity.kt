package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.GeminiConfig
import com.example.ai.configureAppCheck
import com.example.ui.WebForgeApp
import com.example.ui.theme.WebForgeTheme

class MainActivity : ComponentActivity() {

    private var fatalErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before any Firebase / ViewModel initialization or setContent
        configureAppCheck(this, intent)
        GeminiConfig.init(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = throwable.localizedMessage ?: throwable.javaClass.simpleName
            runCatching {
                runOnUiThread { fatalErrorMessage = msg }
            }
            if (previousHandler != null && thread.name != "main") {
                previousHandler.uncaughtException(thread, throwable)
            }
        }

        enableEdgeToEdge()
        setContent {
            val crash = fatalErrorMessage
            if (crash != null) {
                WebForgeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "WebForge Recovery",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = crash,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { fatalErrorMessage = null }) {
                                        Text("Restart Studio")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                WebForgeApp()
            }
        }
    }
}
