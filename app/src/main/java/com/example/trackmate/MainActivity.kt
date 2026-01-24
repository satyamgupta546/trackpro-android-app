package com.example.trackmate.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import com.example.trackmate.UserDataManager

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Data Manager
        UserDataManager.init(this)

        setContent {
            // 2. Check Login Status
            var isLoggedIn by remember { mutableStateOf(UserDataManager.isLoggedIn()) }

            if (isLoggedIn) {
                // === SHOW DASHBOARD (Trips + Profile) ===
                DashboardScreen(
                    onLogout = {
                        isLoggedIn = false // Switches back to Login Screen
                    }
                )
            } else {
                // === SHOW LOGIN ===
                LoginScreen(
                    onLoginSuccess = {
                        isLoggedIn = true // Switches to Dashboard
                    }
                )
            }
        }
    }
}