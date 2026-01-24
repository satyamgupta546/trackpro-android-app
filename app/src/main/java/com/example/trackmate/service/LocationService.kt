package com.example.trackmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.trackmate.SupabaseClient
import com.example.trackmate.UserDataManager
import com.example.trackmate.UserTrackingModel
import com.example.trackmate.TripUpdatePayload
import com.google.android.gms.location.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. WakeLock to keep CPU running
    private var wakeLock: PowerManager.WakeLock? = null

    private var isCreatingTrip = false
    private var tripStartTimeMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        UserDataManager.init(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // 2. Acquire WakeLock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrackMate::LocationWakeLock")
        try {
            wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max
        } catch (e: Exception) {
            Log.e("TRACKING", "WakeLock Error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_START") {
            playStatusSound()
            tripStartTimeMillis = System.currentTimeMillis()

            // 3. Start Foreground Service Immediately
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } catch (e: Exception) {
                    startForeground(1, notification)
                }
            } else {
                startForeground(1, notification)
            }

            // 4. Start Updates
            startLocationUpdates()

        } else if (intent?.action == "ACTION_STOP") {
            playStatusSound()
            serviceScope.launch {
                withContext(NonCancellable) {
                    try {
                        stopLocationUpdates()
                        completeTrip() // <--- CRITICAL FIX INSIDE HERE
                    } catch (e: Exception) {
                        Log.e("TRACKING", "Stop Error: ${e.message}")
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun playStatusSound() {
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            Log.e("TRACKING", "Sound Error: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        // Fix: Get Last Known Location Immediately
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    UserDataManager.updateCurrentLocation(location.latitude, location.longitude)
                }
            }
        } catch (e: SecurityException) {
            Log.e("TRACKING", "Permission missing for lastLocation")
        }

        // Standard Updates
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lng = location.longitude

                    // Update UI State
                    UserDataManager.updateCurrentLocation(lat, lng)
                    UserDataManager.addRoutePoint(lat, lng)

                    // Database Sync
                    if (lat != 0.0 && lng != 0.0) {
                        if (UserDataManager.activeTripId == -1L && !isCreatingTrip) {
                            createTripOnFirstLocation(lat, lng)
                        } else {
                            uploadLocationToCloud(lat, lng)
                        }
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("TRACKING", "Permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("TRACKING", "Request error: ${e.message}")
        }
    }

    private fun createTripOnFirstLocation(lat: Double, lng: Double) {
        if (isCreatingTrip) return
        isCreatingTrip = true

        serviceScope.launch {
            try {
                val timeString = "Started at ${getCurrentTime()}"
                val currentId = UserDataManager.currentUserId
                val currentName = UserDataManager.getUserName() ?: "Unknown"
                val currentPhone = UserDataManager.getUserPhone() ?: ""

                val newTrip = UserTrackingModel(
                    user_id = currentId,
                    user_name = currentName,
                    user_phone = currentPhone,
                    status = "In Progress",
                    start_location = timeString,
                    distance = "0.00 km",
                    duration = "0 min",
                    created_at = java.time.Instant.now().toString(),
                    last_lat = lat,
                    last_lng = lng,
                    route_path = listOf(listOf(lat, lng))
                )

                val result = SupabaseClient.client.from("user_tracking").insert(newTrip) {
                    select()
                }.decodeSingle<UserTrackingModel>()

                result.id?.let { UserDataManager.saveActiveTripId(it) }
                updateLiveUserStatus(lat, lng)

            } catch (e: Exception) {
                Log.e("TRACKING", "Create Failed: ${e.message}")
            } finally {
                isCreatingTrip = false
            }
        }
    }

    private fun uploadLocationToCloud(currentLat: Double, currentLng: Double) {
        serviceScope.launch {
            try {
                val activeTripId = UserDataManager.activeTripId
                if (activeTripId == -1L) return@launch

                val currentRoute = UserDataManager.routePoints.map { listOf(it.latitude, it.longitude) }
                val distString = calculateDistanceList(currentRoute)
                val elapsedMillis = System.currentTimeMillis() - tripStartTimeMillis
                val durationString = "${elapsedMillis / 1000 / 60} min"

                val payload = TripUpdatePayload(
                    status = "In Progress",
                    end_location = null,
                    distance = distString,
                    duration = durationString,
                    route_path = currentRoute,
                    last_lat = currentLat,
                    last_lng = currentLng
                )

                SupabaseClient.client.from("user_tracking").update(payload) {
                    filter { eq("id", activeTripId) }
                }

                updateLiveUserStatus(currentLat, currentLng)

            } catch (e: Exception) {
                Log.e("TRACKING", "Upload Failed: ${e.message}")
            }
        }
    }

    private suspend fun updateLiveUserStatus(lat: Double, lng: Double) {
        val userId = UserDataManager.currentUserId ?: return
        try {
            SupabaseClient.client.from("app_users").update(
                mapOf(
                    "last_lat" to lat,
                    "last_lng" to lng,
                    "last_seen" to java.time.Instant.now().toString()
                )
            ) { filter { eq("id", userId) } }
        } catch (e: Exception) { Log.e("TRACKING", "Live Update Failed: ${e.message}") }
    }

    private suspend fun completeTrip() {
        val activeTripId = UserDataManager.activeTripId
        if (activeTripId != -1L) {
            try {
                // === SAFE ROUTE LOGIC ===
                // 1. Get route from memory
                var finalRoute = UserDataManager.routePoints.map { listOf(it.latitude, it.longitude) }

                // 2. CRITICAL FIX: If memory is empty (app restart/kill), FETCH existing route from DB
                // This prevents overwriting the DB with an empty list.
                if (finalRoute.isEmpty()) {
                    Log.w("TRACKING", "Memory empty on stop. Fetching from DB...")
                    val existingTrip = SupabaseClient.client.from("user_tracking")
                        .select { filter { eq("id", activeTripId) } }
                        .decodeSingle<UserTrackingModel>()

                    finalRoute = existingTrip.route_path ?: emptyList()
                    Log.d("TRACKING", "Recovered ${finalRoute.size} points from DB.")
                }

                // 3. Calculate final distance from the SAFE route
                val distString = calculateDistanceList(finalRoute)

                val lastPoint = finalRoute.lastOrNull()
                val finalLat = lastPoint?.get(0) ?: 0.0
                val finalLng = lastPoint?.get(1) ?: 0.0

                val elapsedMillis = System.currentTimeMillis() - tripStartTimeMillis
                val finalDuration = "${elapsedMillis / 1000 / 60} min"
                val stopTime = "Stopped at ${getCurrentTime()}"

                val payload = TripUpdatePayload(
                    status = "Completed",
                    end_location = stopTime,
                    distance = distString,
                    duration = finalDuration,
                    route_path = finalRoute, // Send the safe route
                    last_lat = finalLat,
                    last_lng = finalLng
                )

                SupabaseClient.client.from("user_tracking").update(payload) {
                    filter { eq("id", activeTripId) }
                }
                UserDataManager.saveActiveTripId(-1L)

            } catch (e: Exception) {
                Log.e("TRACKING", "Close Failed: ${e.message}")
            }
        }
    }

    // New helper that accepts List<List<Double>> directly
    private fun calculateDistanceList(routePath: List<List<Double>>): String {
        if (routePath.size < 2) return "0.00 km"
        var totalMeters = 0.0
        for (i in 0 until routePath.size - 1) {
            val p1 = routePath[i]
            val p2 = routePath[i + 1]
            val gp1 = GeoPoint(p1[0], p1[1])
            val gp2 = GeoPoint(p2[0], p2[1])
            totalMeters += gp1.distanceToAsDouble(gp2)
        }
        return String.format("%.2f km", totalMeters / 1000.0)
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        serviceScope.cancel()
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle("TrackMate Active")
            .setContentText("Recording your route...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tracking_channel", "Tracking Service", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Used to track location in background"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}