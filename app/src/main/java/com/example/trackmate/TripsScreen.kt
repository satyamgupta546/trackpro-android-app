package com.example.trackmate.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent // Import for Touch Fix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.trackmate.SupabaseClient
import com.example.trackmate.UserDataManager
import com.example.trackmate.UserTrackingModel
import com.example.trackmate.service.LocationService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.osmdroid.api.IMapController

// === COLORS ===
private val TripsDarkBg = Color(0xFF0B0E14)
private val TripsBlue = Color(0xFF3B82F6)
private val TripsRed = Color(0xFFEF4444)
private val TripsWhite = Color(0xFFFFFFFF)
private val TripsGray = Color(0xFF9CA3AF)
private val TripsCardBg = Color(0xFF151A21)
private val TripsGreen = Color(0xFF10B981)
private val ProgressBg = Color(0xFF374151)

// === TIMEZONE ===
private val IndiaZone = ZoneId.of("Asia/Kolkata")

// === DATA MODELS ===
@Serializable
data class ZonePoint(val lat: Double, val lng: Double)

@Serializable
data class ZoneModel(
    val id: Int,
    val polygon: List<ZonePoint>,
    val target_km: Double,
    val assigned_user: String?
)

@Serializable
data class UserTargetModel(val lat: Double, val lng: Double, val user_id: String?)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Composable
fun TripsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

    // === STATE ===
    var tripsList by remember { mutableStateOf<List<UserTrackingModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(IndiaZone)) }

    // Map Data
    var assignedZonePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var assignedTargetLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var targetKm by remember { mutableStateOf(0.0) }
    var calculatedTotalKm by remember { mutableStateOf(0.0) }
    var hasActiveGoal by remember { mutableStateOf(false) }

    // Map Controller
    var mapController by remember { mutableStateOf<IMapController?>(null) }
    var hasCenteredMap by remember { mutableStateOf(false) }
    var userClickedRecenter by remember { mutableStateOf(false) }

    // Live Tracking
    val isTripActive = UserDataManager.isTripActive.value
    val routePoints = UserDataManager.routePoints.toList()
    val currentLocation = UserDataManager.currentLocation.value

    // === PERMISSIONS ===
    val requiredPermissions = remember {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            scope.launch(Dispatchers.IO) {
                UserDataManager.clearNewTrip()
                startTrackingService(context)
                UserDataManager.setTripStatus(true)
                delay(1000)
                fetchTripsForDate(selectedDate, { tripsList = it }, { calculatedTotalKm = it })
            }
        } else {
            Toast.makeText(context, "Permissions required", Toast.LENGTH_LONG).show()
        }
    }

    fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // === MAP LOGIC ===
    LaunchedEffect(currentLocation, isTripActive, userClickedRecenter) {
        if (currentLocation != null && mapController != null) {
            if (userClickedRecenter) {
                mapController?.animateTo(currentLocation)
                mapController?.setZoom(18.0)
                userClickedRecenter = false
            } else if (isTripActive) {
                mapController?.animateTo(currentLocation)
            } else if (!hasCenteredMap) {
                mapController?.animateTo(currentLocation)
                mapController?.setZoom(16.0)
                hasCenteredMap = true
            }
        }
    }

    // === FETCH TARGETS ===
    fun fetchUserTargetsAndZones() {
        scope.launch(Dispatchers.IO) {
            try {
                val userId = UserDataManager.currentUserId ?: return@launch
                val targets = SupabaseClient.client.from("user_targets").select { filter { eq("user_id", userId) } }.decodeList<UserTargetModel>()
                assignedTargetLocation = targets.firstOrNull()?.let { GeoPoint(it.lat, it.lng) }

                val zones = SupabaseClient.client.from("zones").select().decodeList<ZoneModel>()
                val myZone = zones.find {
                    it.assigned_user == userId.toString() ||
                            it.assigned_user == "User $userId" ||
                            it.assigned_user?.trim() == "User $userId"
                }

                if (myZone != null) {
                    targetKm = myZone.target_km
                    assignedZonePoints = myZone.polygon.map { GeoPoint(it.lat, it.lng) }
                    hasActiveGoal = true
                } else {
                    targetKm = 0.0
                    assignedZonePoints = emptyList()
                    hasActiveGoal = false
                }
            } catch (e: Exception) { Log.e("TripsScreen", "Fetch Error: ${e.message}") }
        }
    }

    LaunchedEffect(Unit) { fetchUserTargetsAndZones() }

    LaunchedEffect(selectedDate) {
        tripsList = emptyList()
        fetchTripsForDate(selectedDate, { tripsList = it }, { calculatedTotalKm = it })
        while(true) {
            delay(5000)
            fetchTripsForDate(selectedDate, { tripsList = it }, { calculatedTotalKm = it })
            fetchUserTargetsAndZones()
        }
    }

    // === UI: FULL PAGE SCROLL ===
    Scaffold(containerColor = TripsDarkBg) { padding ->
        // Changed from Column to LazyColumn so the whole page scrolls
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {

            // --- SECTION 1: HEADER, MAP, BUTTONS (Scrollable now) ---
            item {
                Column {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedDate = selectedDate.minusDays(1) },
                            modifier = Modifier.size(40.dp).background(TripsCardBg, RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TripsWhite)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(getFancyDateTitle(selectedDate), color = TripsWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), color = TripsGray, fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { if (selectedDate.isBefore(LocalDate.now(IndiaZone))) selectedDate = selectedDate.plusDays(1) },
                            modifier = Modifier.size(40.dp).background(TripsCardBg, RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TripsWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Map
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
                                        controller.setZoom(15.0)
                                        mapController = controller

                                        // === FIX: ALLOW MAP PANNING INSIDE SCROLLVIEW ===
                                        setOnTouchListener { v, event ->
                                            // Disallow parent (LazyColumn) from intercepting touch events
                                            v.parent.requestDisallowInterceptTouchEvent(true)
                                            if (event.action == MotionEvent.ACTION_UP) {
                                                v.parent.requestDisallowInterceptTouchEvent(false)
                                            }
                                            false
                                        }
                                    }
                                },
                                update = { mapView ->
                                    mapView.overlays.clear()
                                    // ... Polygons & Markers ...
                                    if (hasActiveGoal && assignedZonePoints.isNotEmpty()) {
                                        val polygon = Polygon()
                                        polygon.points = assignedZonePoints
                                        polygon.fillPaint.color = android.graphics.Color.parseColor("#4D3B82F6")
                                        polygon.outlinePaint.color = android.graphics.Color.parseColor("#3B82F6")
                                        polygon.outlinePaint.strokeWidth = 5f
                                        mapView.overlays.add(polygon)
                                    }
                                    if (assignedTargetLocation != null) {
                                        val tMarker = Marker(mapView)
                                        tMarker.position = assignedTargetLocation
                                        tMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        tMarker.icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                                        tMarker.title = "Target"
                                        mapView.overlays.add(tMarker)
                                    }
                                    if (routePoints.isNotEmpty()) {
                                        val line = Polyline()
                                        line.setPoints(routePoints)
                                        line.outlinePaint.color = android.graphics.Color.parseColor("#3B82F6")
                                        line.outlinePaint.strokeWidth = 15f
                                        mapView.overlays.add(line)
                                    }
                                    val myPos = currentLocation ?: assignedTargetLocation ?: GeoPoint(12.9716, 77.5946)
                                    val myMarker = Marker(mapView)
                                    myMarker.position = myPos
                                    myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    myMarker.title = "Me"
                                    mapView.overlays.add(myMarker)
                                    mapView.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isTripActive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(TripsGreen.copy(alpha = 0.9f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("● Live", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            FloatingActionButton(
                                onClick = {
                                    userClickedRecenter = true
                                    if (currentLocation == null) {
                                        Toast.makeText(context, "Waiting for location...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                containerColor = TripsWhite,
                                contentColor = TripsBlue,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(45.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    val isToday = selectedDate == LocalDate.now(IndiaZone)
                    Button(
                        onClick = {
                            if (!isToday) { selectedDate = LocalDate.now(IndiaZone); return@Button }
                            if (!hasPermissions()) { permissionLauncher.launch(requiredPermissions); return@Button }
                            scope.launch(Dispatchers.IO) {
                                if (isTripActive) {
                                    stopTrackingService(context)
                                    UserDataManager.setTripStatus(false)
                                } else {
                                    UserDataManager.clearNewTrip()
                                    startTrackingService(context)
                                    UserDataManager.setTripStatus(true)
                                    delay(1000)
                                    fetchTripsForDate(selectedDate, { tripsList = it }, { calculatedTotalKm = it })
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isToday) Color.Gray else if (isTripActive) TripsRed else TripsBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(if (isTripActive) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTripActive) "STOP TRACKING" else "START TRACKING", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Goal Card
                    if (hasActiveGoal && targetKm > 0) {
                        TaskProgressCard(
                            coveredKm = calculatedTotalKm,
                            targetKm = targetKm,
                            onClick = {
                                val dest = assignedTargetLocation ?: assignedZonePoints.firstOrNull()
                                if (dest != null && mapController != null) {
                                    mapController?.animateTo(dest)
                                    mapController?.setZoom(16.0)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text("Activity for ${getFancyDateTitle(selectedDate)}", color = TripsGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // --- SECTION 2: TRIP LIST ---
            if (isLoading && tripsList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TripsBlue)
                    }
                }
            } else if (tripsList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No trips found.", color = Color.Gray)
                    }
                }
            } else {
                items(tripsList) { trip ->
                    TripCard(trip)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// ... (KEEP HELPER FUNCTIONS: calculateRealDistance, fetchTripsForDate, etc. EXACTLY AS BEFORE) ...
// (I will paste them below for completeness if you need to copy the FULL file)

fun calculateRealDistance(routePath: List<List<Double>>?): Double {
    if (routePath.isNullOrEmpty() || routePath.size < 2) return 0.0
    var totalMeters = 0.0
    for (i in 0 until routePath.size - 1) {
        val p1 = routePath[i]
        val p2 = routePath[i+1]
        val gp1 = GeoPoint(p1[0], p1[1])
        val gp2 = GeoPoint(p2[0], p2[1])
        totalMeters += gp1.distanceToAsDouble(gp2)
    }
    return totalMeters / 1000.0
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun fetchTripsForDate(
    date: LocalDate,
    updateList: (List<UserTrackingModel>) -> Unit,
    updateTotalKm: (Double) -> Unit
) {
    try {
        val result = SupabaseClient.client.from("user_tracking")
            .select {
                filter { eq("user_id", UserDataManager.currentUserId) }
                order("created_at", order = Order.DESCENDING)
            }
            .decodeList<UserTrackingModel>()

        val filtered = result.filter {
            try {
                val instant = Instant.parse(it.created_at)
                val localDate = instant.atZone(IndiaZone).toLocalDate()
                localDate == date
            } catch (e: Exception) { false }
        }
        updateList(filtered)

        var sumDist = 0.0
        filtered.forEach { trip ->
            val realDist = calculateRealDistance(trip.route_path)
            sumDist += realDist
        }
        updateTotalKm(sumDist)

        if (date == LocalDate.now(IndiaZone)) {
            val activeTrip = result.find { it.status == "In Progress" }
            if (activeTrip != null) {
                UserDataManager.setTripStatus(true)
                activeTrip.id?.let { UserDataManager.saveActiveTripId(it) }
            }
        }
    } catch (e: Exception) { Log.e("TripsScreen", "Fetch Error: ${e.message}") }
}

@Composable
fun TaskProgressCard(coveredKm: Double, targetKm: Double, onClick: () -> Unit) {
    val percentage = if (targetKm > 0) ((coveredKm / targetKm) * 100).roundToInt().coerceIn(0, 100) else 0
    val emoji = when (percentage) { in 0..20 -> "🙂"; in 21..40 -> "☺️"; in 41..60 -> "😊"; in 61..80 -> "😁"; else -> "😍" }

    Card(
        colors = CardDefaults.cardColors(containerColor = TripsCardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2D3748)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Daily Goal $emoji", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "$percentage% (${String.format("%.2f", coveredKm)} / ${targetKm.toInt()} km)", color = TripsGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ProgressBg)) {
                Box(modifier = Modifier.fillMaxWidth(percentage / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(TripsGreen))
            }
        }
    }
}

fun startTrackingService(context: Context) {
    val intent = Intent(context, LocationService::class.java).apply { action = "ACTION_START" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun stopTrackingService(context: Context) {
    val intent = Intent(context, LocationService::class.java).apply { action = "ACTION_STOP" }
    context.startService(intent)
}

@RequiresApi(Build.VERSION_CODES.O)
fun getFancyDateTitle(date: LocalDate): String {
    val today = LocalDate.now(IndiaZone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }
}

@Composable
fun TripCard(trip: UserTrackingModel) {
    val isLive = trip.status == "In Progress"
    val realDist = calculateRealDistance(trip.route_path)
    val distText = String.format("%.2f km", realDist)

    Card(
        colors = CardDefaults.cardColors(containerColor = TripsCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLive) TripsBlue.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isLive) Icons.Default.DirectionsCar else Icons.Default.CheckCircle,
                    tint = if (isLive) TripsBlue else TripsGray,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val timeStr = trip.created_at?.take(16)?.replace("T", " ") ?: "Unknown"
                Text(
                    if (trip.start_location?.contains("Started") == true) trip.start_location else "Trip at $timeStr",
                    color = TripsWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    if (isLive) "Live Now" else "$distText • ${if (!trip.duration.isNullOrEmpty()) trip.duration else "0 min"}",
                    color = if(isLive) TripsGreen else TripsGray,
                    fontSize = 12.sp
                )
            }
            Text(if(isLive) "Active" else "Done", color = TripsGray, fontSize = 12.sp)
        }
    }
}