package com.example.trackmate.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackmate.UserDataManager
import com.example.trackmate.SupabaseManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

// === 🎨 UNIQUE COLORS (Internal Fix Only) ===
// These names are unique to this file so they won't conflict with other files
private val LgnBg = Color(0xFF0B0E14)         // Deep Dark Background
private val LgnInputBg = Color(0xFF151A21)    // Dark Input Field Background
private val LgnBlue = Color(0xFF3B82F6)       // Primary Blue
private val LgnTextWhite = Color(0xFFFFFFFF)
private val LgnTextGray = Color(0xFF9CA3AF)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // State Variables
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var referCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LgnBg)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // === 1. LOGO ICON ===
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Brush.linearGradient(listOf(LgnBlue, Color(0xFF60A5FA))),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation, // Paper plane / Navigation icon
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === 2. TITLE ===
            Text(
                text = "Location Tracker",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = LgnTextWhite
            )
            Text(
                text = "Track your journeys in real-time",
                fontSize = 14.sp,
                color = LgnTextGray
            )

            Spacer(modifier = Modifier.height(40.dp))

            // === 3. FORM SECTION ===
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Get Started",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LgnTextWhite
                )
                Text(
                    text = "Enter your details to begin tracking",
                    fontSize = 13.sp,
                    color = LgnTextGray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // -- FULL NAME --
                LoginInputField(
                    label = "Full Name",
                    value = fullName,
                    onValueChange = { fullName = it },
                    icon = Icons.Default.Person,
                    placeholder = "Enter your full name"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // -- EMAIL --
                LoginInputField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    icon = Icons.Default.Email,
                    placeholder = "Enter your email",
                    keyboardType = KeyboardType.Email
                )

                Spacer(modifier = Modifier.height(16.dp))

                // -- PHONE NUMBER --
                LoginInputField(
                    label = "Phone Number",
                    value = phone,
                    onValueChange = { phone = it },
                    icon = Icons.Default.Phone,
                    placeholder = "+91 XXXXX XXXXX",
                    keyboardType = KeyboardType.Phone
                )

                Spacer(modifier = Modifier.height(16.dp))

                // -- REFER CODE --
                LoginInputField(
                    label = "Refer Code",
                    value = referCode,
                    onValueChange = { referCode = it },
                    icon = Icons.Outlined.CardGiftcard,
                    placeholder = "ENTER REFERRAL CODE"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // === 4. LOGIN BUTTON ===
            Button(
                onClick = {
                    if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                        Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                    } else {
                        isLoading = true
                        scope.launch {
                            try {
                                // 1. Save Locally
                                UserDataManager.saveUser(fullName, phone, email)

                                // 2. Send to Supabase (Ignore if exists)
                                try {
                                    val userMap = mapOf(
                                        "name" to fullName,
                                        "email" to email,
                                        "status" to "Active"
                                        // "refer_code" to referCode (Add to DB later if needed)
                                    )
                                    SupabaseManager.client.from("users").insert(userMap)
                                } catch (e: Exception) {
                                    // User might already exist, safe to ignore
                                }

                                Toast.makeText(context, "Welcome, $fullName!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LgnBlue),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // === 5. FOOTER ===
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = LgnTextGray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Location permission required for tracking", color = LgnTextGray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// === CUSTOM INPUT FIELD COMPONENT (Matches Your Design) ===
@Composable
fun LoginInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LgnBlue, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = LgnTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (label != "Refer Code") {
                Text(" *", color = Color.Red, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.Gray, fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LgnInputBg,
                unfocusedContainerColor = LgnInputBg,
                disabledContainerColor = LgnInputBg,
                focusedBorderColor = LgnBlue.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent, // No border when inactive, like screenshot
                focusedTextColor = LgnTextWhite,
                unfocusedTextColor = LgnTextWhite
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            singleLine = true
        )
    }
}