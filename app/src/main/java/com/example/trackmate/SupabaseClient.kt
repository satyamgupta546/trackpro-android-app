package com.example.trackmate

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.Serializable // <--- ADD THIS IMPORT

object SupabaseManager {
    // REPLACE THESE WITH YOUR KEYS FROM SUPABASE DASHBOARD
    val client = createSupabaseClient(
        supabaseUrl = "https://bhnrfnkiebfpghkzxvwy.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobnJmbmtpZWJmcGdoa3p4dnd5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxNTM1MjEsImV4cCI6MjA4MzcyOTUyMX0.kky00iaoiepdPKyUm4Ti-RWbelZTd3no173YAP479B8"
    ) {
        install(Postgrest)
    }
}

