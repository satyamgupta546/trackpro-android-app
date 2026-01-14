package com.example.trackmate.ui

import android.Manifest
import com.example.trackmate.TripUpdatePayload
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.trackmate.SupabaseManager
import com.example.trackmate.UserDataManager
import com.example.trackmate.UserTrackingModel
import com.example.trackmate.service.LocationService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable // <--- THE ONLY SERIALIZABLE IMPORT
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

// === 🎨 COLORS ===
private val TripsDarkBg = Color(0xFF0B0E14)
private val TripsBlue = Color(0xFF3B82F6)
private val TripsRed = Color(0xFFEF4444)
private val TripsWhite = Color(0xFFFFFFFF)
private val TripsGray = Color(0xFF9CA3AF)
private val TripsCardBg = Color(0xFF151A21)
private val TripsGreen = Color(0xFF10B981)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TripsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

    var tripsList by remember { mutableStateOf<List<UserTrackingModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val isTripActive = UserDataManager.isTripActive.value
    val routePoints = UserDataManager.routePoints.toList()
    val currentLocation = UserDataManager.currentLocation.value

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            Toast.makeText(context, "Permissions Granted! Click Start.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasPermissions(): Boolean {
        val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return loc && notif
    }

    fun fetchTripsForDate(date: LocalDate) {
        scope.launch {
            isLoading = true
            try {
                val startOfDay = "${date}T00:00:00"
                val endOfDay = "${date}T23:59:59"

                val result = SupabaseManager.client
                    .from("user_tracking")
                    .select {
                        filter {
                            eq("user_phone", UserDataManager.getUserPhone() ?: "")
                            gte("created_at", startOfDay)
                            lte("created_at", endOfDay)
                        }
                        order("created_at", order = Order.DESCENDING)
                    }
                    .decodeList<UserTrackingModel>()

                tripsList = result

                if (date == LocalDate.now() && result.isNotEmpty() && result[0].status == "In Progress") {
                    UserDataManager.setTripStatus(true)
                    UserDataManager.saveActiveTripId(result[0].id ?: -1L)
                }
            } catch (e: Exception) {
                Log.e("TripsScreen", "Fetch Error", e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDate) {
        fetchTripsForDate(selectedDate)
    }

    Scaffold(containerColor = TripsDarkBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }, modifier = Modifier.size(32.dp).background(TripsCardBg, RoundedCornerShape(8.dp))) {
                    Icon(Icons.Default.ChevronLeft, null, tint = TripsWhite)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getFancyDateTitle(selectedDate), color = TripsWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(selectedDate.toString(), color = TripsGray, fontSize = 12.sp)
                }
                IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }, modifier = Modifier.size(32.dp).background(TripsCardBg, RoundedCornerShape(8.dp))) {
                    Icon(Icons.Default.ChevronRight, null, tint = TripsWhite)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MAP
            Card(
                colors = CardDefaults.cardColors(containerColor = TripsCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if(isTripActive) TripsGreen else Color.Gray.copy(alpha=0.3f)),
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(18.0)
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.clear()
                            if (routePoints.isNotEmpty()) {
                                val line = Polyline()
                                line.setPoints(routePoints)
                                line.outlinePaint.color = android.graphics.Color.parseColor("#3B82F6")
                                line.outlinePaint.strokeWidth = 15f
                                mapView.overlays.add(line)
                            }
                            val myPos = currentLocation ?: GeoPoint(12.9716, 77.5946)
                            val marker = Marker(mapView)
                            marker.position = myPos
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.title = "Me"
                            mapView.overlays.add(marker)

                            if (isTripActive && currentLocation != null) mapView.controller.animateTo(currentLocation)
                            else if (!isTripActive && routePoints.isEmpty()) mapView.controller.setCenter(myPos)

                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isTripActive) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(20.dp)).background(TripsGreen.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("● Live", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BUTTON
            val isToday = selectedDate == LocalDate.now()
            Button(
                onClick = {
                    if (!isToday) {
                        selectedDate = LocalDate.now()
                        return@Button
                    }
                    if (!hasPermissions()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                        return@Button
                    }

                    scope.launch(Dispatchers.IO) {
                        if (isTripActive) {
                            // === STOP TRACKING ===
                            stopTrackingService(context)
                            UserDataManager.setTripStatus(false)

                            val activeTripId = UserDataManager.getActiveTripId()
                            val totalDistKm = calculateTotalDistance(routePoints)
                            val distanceStr = String.format("%.2f km", totalDistKm)
                            val routeList = routePoints.map { listOf(it.latitude, it.longitude) }

                            try {
                                if (activeTripId != -1L) {
                                    val updatePayload = TripUpdatePayload(
                                        status = "Completed",
                                        end_location = "Stopped at ${getCurrentTime()}",
                                        distance = distanceStr,
                                        route_path = routeList
                                    )

                                    SupabaseManager.client.from("user_tracking").update(updatePayload) {
                                        filter { eq("id", activeTripId) }
                                    }

                                    UserDataManager.saveActiveTripId(-1L)
                                    fetchTripsForDate(selectedDate)
                                }
                            } catch (e: Exception) {
                                Log.e("TrackMate", "Error stopping: ${e.message}")
                            }

                        } else {
                            // === START TRACKING ===
                            startTrackingService(context)
                            UserDataManager.setTripStatus(true)

                            val userPhone = UserDataManager.getUserPhone() ?: "No Phone"
                            val userName = UserDataManager.getUserName() ?: "Unknown"
                            val userEmail = UserDataManager.getUserEmail() ?: "No Email"

                            val trackingData = UserTrackingModel(
                                user_name = userName,
                                user_phone = userPhone,
                                user_email = userEmail,
                                status = "In Progress",
                                start_location = "Started at ${getCurrentTime()}",
                                route_path = emptyList()
                            )

                            try {
                                val result = SupabaseManager.client.from("user_tracking")
                                    .insert(trackingData) { select() }
                                    .decodeSingle<UserTrackingModel>()

                                val newId = result.id ?: -1L
                                UserDataManager.saveActiveTripId(newId)

                                fetchTripsForDate(selectedDate)
                            } catch (e: Exception) {
                                Log.e("TrackMate", "Error starting trip: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (!isToday) Color.Gray else if (isTripActive) TripsRed else TripsBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(if (isTripActive) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isTripActive) "STOP TRACKING" else "START TRACKING", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Activity for ${getFancyDateTitle(selectedDate)}", color = TripsGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(tripsList) { trip -> TripCard(trip) }
            }
        }
    }
}

// === HELPERS ===


fun startTrackingService(context: Context) {
    val intent = Intent(context, LocationService::class.java).apply { action = "ACTION_START" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
    else context.startService(intent)
}

fun stopTrackingService(context: Context) {
    val intent = Intent(context, LocationService::class.java).apply { action = "ACTION_STOP" }
    context.startService(intent)
}

fun calculateTotalDistance(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0
    var totalMeters = 0.0
    for (i in 0 until points.size - 1) {
        totalMeters += points[i].distanceToAsDouble(points[i+1])
    }
    return totalMeters / 1000.0
}

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.US).format(Date())
}

@RequiresApi(Build.VERSION_CODES.O)
fun getFancyDateTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }
}

@Composable
fun TripCard(trip: UserTrackingModel) {
    Card(colors = CardDefaults.cardColors(containerColor = TripsCardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (trip.status == "In Progress") TripsBlue.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(if (trip.status == "In Progress") Icons.Default.DirectionsCar else Icons.Default.CheckCircle, tint = if (trip.status == "In Progress") TripsBlue else TripsGray, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.start_location ?: "Unknown", color = TripsWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val details = if (trip.status == "In Progress") "Live Now" else "${trip.distance ?: "0 km"}"
                Text(details, color = if(trip.status == "In Progress") TripsGreen else TripsGray, fontSize = 12.sp)
            }
            Text(if(trip.status == "In Progress") "Active" else "Done", color = TripsGray, fontSize = 12.sp)
        }
    }
}