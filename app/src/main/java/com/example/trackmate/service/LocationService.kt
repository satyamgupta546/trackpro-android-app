package com.example.trackmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.trackmate.SupabaseManager
import com.example.trackmate.UserDataManager
import com.google.android.gms.location.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.osmdroid.util.GeoPoint

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // Track when this trip started
    private var serviceStartTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_START") {
            // Set start time if not already set
            if (serviceStartTime == 0L) serviceStartTime = System.currentTimeMillis()

            startForeground(1, createNotification())
            startLocationUpdates()
        } else if (intent?.action == "ACTION_STOP") {
            stopLocationUpdates()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lng = location.longitude
                    Log.i("TRACKING", "New Location: $lat, $lng")

                    // 1. Save Locally
                    UserDataManager.updateCurrentLocation(lat, lng)
                    UserDataManager.addRoutePoint(lat, lng)

                    // 2. UPLOAD EVERYTHING (Location + Distance + Duration)
                    uploadLocationToCloud()
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("TRACKING", "Permission lost: ${e.message}")
        }
    }

    private fun uploadLocationToCloud() {
        serviceScope.launch {
            try {
                val activeTripId = UserDataManager.getActiveTripId()
                if (activeTripId == -1L) return@launch

                // Get Data from Manager
                val currentRoute = UserDataManager.routePoints.map { listOf(it.latitude, it.longitude) }
                val currentLat = UserDataManager.currentLocation.value?.latitude ?: 0.0
                val currentLng = UserDataManager.currentLocation.value?.longitude ?: 0.0

                // === NEW CALCULATIONS ===
                val distString = calculateDistance(UserDataManager.routePoints)
                val durString = calculateDuration()

                // Prepare Payload
                val payload = LiveLocationUpdate(
                    route_path = currentRoute,
                    last_lat = currentLat,
                    last_lng = currentLng,
                    distance = distString,
                    duration = durString
                )

                SupabaseManager.client.from("user_tracking").update(payload) {
                    filter { eq("id", activeTripId) }
                }
                Log.i("TRACKING", "Uploaded: $distString | $durString")

            } catch (e: Exception) {
                Log.e("TRACKING", "Upload Failed: ${e.message}")
            }
        }
    }

    // --- HELPER: Calculate Distance in KM ---
    private fun calculateDistance(points: List<GeoPoint>): String {
        if (points.size < 2) return "0.00 km"
        var totalMeters = 0.0
        for (i in 0 until points.size - 1) {
            totalMeters += points[i].distanceToAsDouble(points[i + 1])
        }
        return String.format("%.2f km", totalMeters / 1000.0)
    }

    // --- HELPER: Calculate Duration in Minutes ---
    private fun calculateDuration(): String {
        if (serviceStartTime == 0L) return "0 min"
        val diff = System.currentTimeMillis() - serviceStartTime
        val minutes = diff / (1000 * 60)
        return "$minutes min"
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle("TrackMate Active")
            .setContentText("Tracking distance & time...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tracking_channel", "Tracking Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// === UPDATED DATA CLASS (Now includes distance & duration) ===
@Serializable
data class LiveLocationUpdate(
    val last_lat: Double,
    val last_lng: Double,
    val route_path: List<List<Double>>,
    val distance: String,
    val duration: String
)