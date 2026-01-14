package com.example.trackmate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.trackmate.ui.LocationManager // <--- ADDED IMPORT
import com.example.trackmate.ui.LoginScreen
import com.example.trackmate.ui.ProfileScreen
import com.example.trackmate.ui.TripsScreen
import com.example.trackmate.ui.theme.TrackMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Managers
        UserDataManager.init(this)
        LocationManager.init(this) // <--- CRITICAL: This enables the Debug Toasts!

        setContent {
            TrackMateTheme {
                val context = LocalContext.current

                // 2. Setup launchers for permissions
                val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(context, "Set Location to 'Allow all the time' in Settings for background tracking", Toast.LENGTH_LONG).show()
                    }
                }

                val foregroundPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allForegroundGranted = permissions.entries.all { it.value }
                    if (allForegroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // After foreground is granted, check/ask for background
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }

                // 3. Initial Permission Check (Runs only once on App Start)
                LaunchedEffect(Unit) {
                    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                    if (!hasFineLocation || !hasCoarseLocation) {
                        foregroundPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val hasBackgroundLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!hasBackgroundLocation) {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }

                RootNavigation()
            }
        }
    }
}

@Composable
fun RootNavigation() {
    val navController = rememberNavController()
    // Check login status
    val startScreen = if (UserDataManager.isUserLoggedIn()) "main_app" else "login"

    NavHost(navController = navController, startDestination = startScreen) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main_app") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("main_app") {
            MainAppScreen(
                onLogout = {
                    UserDataManager.logout()
                    navController.navigate("login") {
                        popUpTo("main_app") { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun MainAppScreen(onLogout: () -> Unit) {
    val bottomNavController = rememberNavController()
    val items = listOf("Trips", "Profile")
    val icons = listOf(Icons.Default.Map, Icons.Default.Person)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B0E14), contentColor = Color.White) {
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = currentRoute == item.lowercase(),
                        onClick = {
                            bottomNavController.navigate(item.lowercase()) {
                                popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF3B82F6),
                            selectedTextColor = Color(0xFF3B82F6),
                            indicatorColor = Color(0xFF151A21),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = bottomNavController, startDestination = "trips", modifier = Modifier.padding(innerPadding)) {
            composable("trips") { TripsScreen() }
            composable("profile") { ProfileScreen(onBackClick = { }, onLogoutClick = onLogout) }
        }
    }
}