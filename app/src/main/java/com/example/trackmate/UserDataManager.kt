package com.example.trackmate

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.osmdroid.util.GeoPoint

object UserDataManager {
    private const val PREF_NAME = "TrackMatePrefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_PHONE = "user_phone"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_TRIP_ACTIVE = "trip_active"
    private const val KEY_ACTIVE_TRIP_ID = "active_trip_id"

    private lateinit var prefs: SharedPreferences

    // Live State
    var isTripActive = mutableStateOf(false)
    var currentLocation = mutableStateOf<GeoPoint?>(null)

    // Live Route for Map
    var routePoints = mutableStateListOf<GeoPoint>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isTripActive.value = prefs.getBoolean(KEY_TRIP_ACTIVE, false)
    }

    // --- AUTHENTICATION ---
    fun saveUser(name: String, phone: String, email: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_PHONE, phone)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun isUserLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getUserPhone(): String? = prefs.getString(KEY_USER_PHONE, null)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun logout() {
        prefs.edit().clear().apply()
        isTripActive.value = false
        routePoints.clear()
        currentLocation.value = null
    }

    // --- TRIP MANAGEMENT ---

    fun setTripStatus(isActive: Boolean) {
        isTripActive.value = isActive
        prefs.edit().putBoolean(KEY_TRIP_ACTIVE, isActive).apply()
        if (!isActive) routePoints.clear()
    }

    fun saveActiveTripId(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_TRIP_ID, id).apply()
    }

    fun getActiveTripId(): Long {
        return prefs.getLong(KEY_ACTIVE_TRIP_ID, -1L)
    }

    // --- LIVE MAP DATA ---

    // 1. Updates the "Me" marker
    fun updateCurrentLocation(lat: Double, lng: Double) {
        currentLocation.value = GeoPoint(lat, lng)
    }

    // 2. PRIMARY: Adds point using a GeoPoint object
    fun addRoutePoint(point: GeoPoint) {
        if (isTripActive.value) {
            routePoints.add(point)
        }
    }

    // === 3. THIS WAS MISSING! (The Helper Function) ===
    // This allows you to send two numbers instead of a GeoPoint
    fun addRoutePoint(lat: Double, lng: Double) {
        addRoutePoint(GeoPoint(lat, lng))
    }
}