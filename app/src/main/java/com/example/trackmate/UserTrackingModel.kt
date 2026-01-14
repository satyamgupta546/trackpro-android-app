package com.example.trackmate

import kotlinx.serialization.Serializable

// This suppression fixes the "Property Name" and "Opt-in" errors
@Suppress("PropertyName", "OPT_IN_USAGE")
@Serializable
data class UserTrackingModel(
    val id: Long? = null,

    val created_at: String? = null,
    val user_name: String? = null,
    val user_phone: String? = null,
    val user_email: String? = null,
    val status: String? = null,
    val start_location: String? = null,
    val end_location: String? = null,
    val distance: String? = null,
    val duration: String? = null,

    val route_path: List<List<Double>>? = null,

    val last_lat: Double? = null,
    val last_lng: Double? = null
)

// === NEW HELPER CLASS ADDED HERE ===
// This helps update the trip without errors
@Serializable
data class TripUpdatePayload(
    val status: String,
    val end_location: String,
    val distance: String,
    val route_path: List<List<Double>>
)