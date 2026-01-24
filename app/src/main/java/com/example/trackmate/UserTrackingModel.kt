package com.example.trackmate

import kotlinx.serialization.Serializable

@Serializable
data class UserTrackingModel(
    val id: Long? = null,
    val user_id: Long? = null,
    val user_name: String? = null,
    val user_phone: String? = null,
    val status: String? = null,
    val start_location: String? = null,
    val end_location: String? = null,
    val distance: String? = null,
    val duration: String? = null, // Stores "15 min"
    val route_path: List<List<Double>>? = null,
    val last_lat: Double? = null,
    val last_lng: Double? = null,
    val created_at: String? = null
)

@Serializable
data class TripUpdatePayload(
    val status: String,
    val end_location: String?,
    val distance: String,
    val duration: String, // <--- ADDED THIS FIELD
    val route_path: List<List<Double>>,
    val last_lat: Double,
    val last_lng: Double
)