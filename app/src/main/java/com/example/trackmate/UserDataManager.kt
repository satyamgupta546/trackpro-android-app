package com.example.trackmate

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.osmdroid.util.GeoPoint

object UserDataManager {
    private const val PREF_NAME = "trackmate_prefs"
    private lateinit var prefs: SharedPreferences

    // === LIVE TRIP STATE ===
    val isTripActive = mutableStateOf(false)
    val routePoints = mutableStateListOf<GeoPoint>()
    val currentLocation = mutableStateOf<GeoPoint?>(null)

    // === AUTH STATE ===
    var currentUserId: Long = -1L
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getLong("USER_ID", -1L)

        // Restore active state if app was killed
        if (activeTripId != -1L) {
            isTripActive.value = true
            restoreRouteLocally()
        }
    }

    // === AUTH FUNCTIONS ===
    fun saveUser(id: Long, name: String, phone: String, email: String) {
        currentUserId = id
        prefs.edit().apply {
            putLong("USER_ID", id)
            putString("USER_NAME", name)
            putString("USER_PHONE", phone)
            putString("USER_EMAIL", email)
            putBoolean("IS_LOGGED_IN", true)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("IS_LOGGED_IN", false)
    }

    fun logout() {
        currentUserId = -1L
        prefs.edit().clear().apply()
        routePoints.clear()
        isTripActive.value = false
    }

    fun getUserPhone(): String? = prefs.getString("USER_PHONE", null)
    fun getUserName(): String? = prefs.getString("USER_NAME", null)
    fun getUserEmail(): String? = prefs.getString("USER_EMAIL", null)

    // === TRIP FUNCTIONS ===
    fun saveActiveTripId(id: Long) {
        prefs.edit().putLong("ACTIVE_TRIP_ID", id).apply()
    }

    val activeTripId: Long
        get() = prefs.getLong("ACTIVE_TRIP_ID", -1L)

    // **UPDATED**: Only toggle the boolean. DO NOT CLEAR DATA HERE.
    fun setTripStatus(active: Boolean) {
        isTripActive.value = active
        // Logic change: We keep routePoints and currentLocation visible even after stop
        // so the user can see where they just walked.
    }

    // **NEW**: Call this ONLY when starting a brand new trip
    fun clearNewTrip() {
        routePoints.clear()
        // We keep currentLocation so the map doesn't snap to (0,0)
        prefs.edit().remove("SAVED_ROUTE_PATH").apply()
    }

    fun updateCurrentLocation(lat: Double, lng: Double) {
        currentLocation.value = GeoPoint(lat, lng)
    }

    fun addRoutePoint(lat: Double, lng: Double) {
        routePoints.add(GeoPoint(lat, lng))

        // Save locally for crash safety
        val oldString = prefs.getString("SAVED_ROUTE_PATH", "")
        val newPoint = "$lat,$lng"
        val newString = if (oldString.isNullOrEmpty()) newPoint else "$oldString;$newPoint"
        prefs.edit().putString("SAVED_ROUTE_PATH", newString).apply()
    }

    private fun restoreRouteLocally() {
        val savedString = prefs.getString("SAVED_ROUTE_PATH", "")
        if (!savedString.isNullOrEmpty()) {
            val pairs = savedString.split(";")
            routePoints.clear()
            pairs.forEach { pair ->
                val parts = pair.split(",")
                if (parts.size == 2) {
                    try {
                        val lat = parts[0].toDouble()
                        val lng = parts[1].toDouble()
                        routePoints.add(GeoPoint(lat, lng))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}