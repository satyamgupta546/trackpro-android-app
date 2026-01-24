package com.example.trackmate

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

object SupabaseClient {

    // Your existing client initialization (Keep this as it is)
    val client = createSupabaseClient(
        supabaseUrl = "https://bhnrfnkiebfpghkzxvwy.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobnJmbmtpZWJmcGdoa3p4dnd5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxNTM1MjEsImV4cCI6MjA4MzcyOTUyMX0.kky00iaoiepdPKyUm4Ti-RWbelZTd3no173YAP479B8"
    ) {
        install(Postgrest)
    }

    // === NEW FUNCTION: CHECK OR CREATE USER ===
    suspend fun loginOrRegisterUser(name: String, phone: String, email: String): Long? {
        return try {
            // 1. Check if user exists by Phone
            val existingUser = client.from("app_users").select {
                filter { eq("phone", phone) }
            }.decodeSingleOrNull<AppUser>()

            if (existingUser != null) {
                Log.i("AUTH", "User found: ${existingUser.id}")
                return existingUser.id // Found them! Return existing ID (e.g., 2)
            } else {
                // 2. Not found? Create new user
                val newUser = AppUser(name = name, phone = phone, email = email)
                val result = client.from("app_users").insert(newUser) {
                    select()
                }.decodeSingle<AppUser>()

                Log.i("AUTH", "New user created: ${result.id}")
                return result.id // Created! Return new ID
            }
        } catch (e: Exception) {
            Log.e("AUTH", "Login Error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

// === NEW DATA CLASS ===
@Serializable
data class AppUser(
    val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String
)