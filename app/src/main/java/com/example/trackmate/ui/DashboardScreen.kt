package com.example.trackmate.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// === DASHBOARD COLORS ===
private val NavBg = Color(0xFF0F172A)
private val ActiveBlue = Color(0xFF3B82F6)
private val InactiveGray = Color(0xFF64748B)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Trips, 1 = Profile

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = NavBg,
                contentColor = ActiveBlue
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, contentDescription = "Trips") },
                    label = { Text("Trips") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ActiveBlue,
                        selectedTextColor = ActiveBlue,
                        unselectedIconColor = InactiveGray,
                        unselectedTextColor = InactiveGray,
                        indicatorColor = ActiveBlue.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ActiveBlue,
                        selectedTextColor = ActiveBlue,
                        unselectedIconColor = InactiveGray,
                        unselectedTextColor = InactiveGray,
                        indicatorColor = ActiveBlue.copy(alpha = 0.2f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (selectedTab == 0) {
                // === FIXED: Removed the dailyDistance argument to match TripsScreen ===
                TripsScreen()
            } else {
                ProfileScreen(onLogout = onLogout)
            }
        }
    }
}