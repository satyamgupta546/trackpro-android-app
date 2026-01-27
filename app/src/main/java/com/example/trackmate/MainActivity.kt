package com.example.trackmate.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.trackmate.UserDataManager
import com.example.trackmate.ui.theme.TrackMateTheme
// We don't strictly need BuildConfig anymore if we remove the check,
// but keeping the import doesn't hurt.
import com.example.trackmate.BuildConfig

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Data Manager (Safe Init)
        try {
            UserDataManager.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            TrackMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    // --- SECURITY CHECK ---
                    var isDevModeEnabled by remember { mutableStateOf(checkDevOptions(context)) }

                    // Re-check when user returns to app (Resume)
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                isDevModeEnabled = checkDevOptions(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (isDevModeEnabled) {
                        // BLOCK USER until they turn it off
                        DevModeAlert()
                    } else {
                        // --- NORMAL APP FLOW ---
                        var isLoggedIn by remember { mutableStateOf(UserDataManager.isLoggedIn()) }

                        if (isLoggedIn) {
                            DashboardScreen(
                                onLogout = {
                                    UserDataManager.logout()
                                    isLoggedIn = false
                                }
                            )
                        } else {
                            LoginScreen(
                                onLoginSuccess = {
                                    isLoggedIn = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Helper to check Global Settings
    private fun checkDevOptions(context: Context): Boolean {
        // === REMOVED THE DEBUG BYPASS ===
        // Now this will run on ALL devices, including your test phone.

        return try {
            val devOptions = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            devOptions == 1
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun DevModeAlert() {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal */ },
        title = { Text(text = "Security Alert", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "Developer Options are enabled on this device.\n\nTo ensure accurate location tracking and security, please turn off Developer Options in Settings.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }) {
                Text("Open Settings")
            }
        }
    )
}