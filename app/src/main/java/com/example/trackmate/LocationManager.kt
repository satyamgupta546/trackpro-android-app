package com.example.trackmate.ui

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.trackmate.SupabaseClient
import com.example.trackmate.UserDataManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

object LocationManager {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private var lastUpdateTime: Long = 0
    private const val UPDATE_INTERVAL = 10 * 1000 // 10 Seconds

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun updateLocation(location: Location) {
        _currentLocation.value = location

        // === FIXED LINE IS HERE ===
        // We removed "point =" so it correctly sends two numbers (lat, lng)
        UserDataManager.addRoutePoint(location.latitude, location.longitude)

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            sendLocationToSupabase(location)
        }
    }

    private fun sendLocationToSupabase(location: Location) {
        val activeRowId = UserDataManager.activeTripId

        if (activeRowId == -1L) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Fetch current path
                val currentData = SupabaseClient.client.from("user_tracking")
                    .select(columns = Columns.list("route_path")) {
                        filter { eq("id", activeRowId) }
                    }.decodeSingleOrNull<TripPathUpdate>()

                // 2. Add new point
                val updatedPath = currentData?.route_path?.toMutableList() ?: mutableListOf()
                updatedPath.add(listOf(location.latitude, location.longitude))

                // 3. Update Database
                SupabaseClient.client.from("user_tracking").update(
                    mapOf(
                        "last_lat" to location.latitude,
                        "last_lng" to location.longitude,
                        "route_path" to updatedPath
                    )
                ) {
                    filter { eq("id", activeRowId) }
                }

                showToast("✅ Cloud Sync Success!")

            } catch (e: Exception) {
                showToast("❌ Sync Failed: ${e.message}")
                Log.e("LocationManager", "Error", e)
            }
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            appContext?.let {
                Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Serializable
data class TripPathUpdate(val route_path: List<List<Double>>? = null)