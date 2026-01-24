package com.example.trackmate.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUser(
    val id: Long? = null,      // Primary Key from Supabase
    val name: String,
    val phone: String,         // Used for Login
    val email: String,
    val password: String       // Simple auth
)