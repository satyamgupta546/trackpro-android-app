package com.example.trackmate.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackmate.SupabaseManager
import com.example.trackmate.UserDataManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

// === 🎨 PREMIUM COLORS (Renamed to avoid conflicts) ===
private val LoginDeepBg = Color(0xFF050B14)
private val LoginAccentBlue = Color(0xFF007BFF)
private val LoginGradientStart = Color(0xFF0061FF) // Deep Royal Blue
private val LoginGradientEnd = Color(0xFF60EFFF)   // Bright Neon Cyan
private val LoginGlassSurface = Color(0xFF1E293B).copy(alpha = 0.5f)
private val LoginTextMuted = Color(0xFF94A3B8)
private val LoginBorderSubtle = Color(0xFF334155)

@Composable
fun RegistrationScreen(onContinueClick: () -> Unit) {
    // Inputs
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var referCode by remember { mutableStateOf("") }

    // UI States
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginDeepBg)
    ) {
        // === 1. HEADER GLOWING ORB ===
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .size(300.dp)
                .blur(radius = 80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LoginGradientStart.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // === 2. CENTERED APP ICON (Fixed: Paper Plane Logo) ===
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(LoginGradientStart, LoginGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // CHANGED: Using 'Navigation' icon to match the Web Design
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === 3. HEADLINES ===
            Text(
                text = "Location Tracker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Track your journeys in real-time",
                fontSize = 15.sp,
                color = LoginTextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // === 4. GLASS-MORPHISM FORM CARD ===
            Card(
                colors = CardDefaults.cardColors(containerColor = LoginGlassSurface),
                border = BorderStroke(1.dp, LoginBorderSubtle.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Get Started",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Create your account to continue",
                        fontSize = 13.sp,
                        color = LoginTextMuted,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // -- INPUTS --

                    GlassInput(
                        label = "Full Name",
                        value = fullName,
                        onValueChange = { fullName = it },
                        icon = Icons.Outlined.Person
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassInput(
                        label = "Email Address",
                        value = email,
                        onValueChange = { email = it },
                        icon = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassInput(
                        label = "Phone Number",
                        value = phone,
                        onValueChange = { phone = it },
                        icon = Icons.Outlined.Phone,
                        keyboardType = KeyboardType.Phone,
                        placeholder = "+91 98765 43210"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassInput(
                        label = "Refer Code",
                        value = referCode,
                        onValueChange = { referCode = it.uppercase() },
                        icon = Icons.Outlined.CardGiftcard,
                        placeholder = "REF-CODE"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // === 5. SUBMIT BUTTON ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 20.dp, spotColor = LoginGradientStart, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(LoginGradientStart, LoginGradientEnd)
                        )
                    )
                    .clickable(enabled = !isLoading) {
                        if (fullName.isBlank() || email.isBlank() || phone.isBlank() || referCode.isBlank()) {
                            Toast.makeText(context, "⚠️ All fields are required", Toast.LENGTH_SHORT).show()
                        } else {
                            isLoading = true

                            // 1. Save Local
                            UserDataManager.saveUser(fullName, phone, email)

                            // 2. Cloud Save
                            scope.launch {
                                try {
                                    val userMap = mapOf(
                                        "name" to fullName,
                                        "email" to email,
                                        "status" to "Active",
                                        "phone" to phone // <--- ADD THIS LINE
                                    )
                                    SupabaseManager.client.from("users").insert(userMap)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                    onContinueClick()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // === 6. FOOTER ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = LoginTextMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Location permission required", color = LoginTextMuted, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, LoginGradientStart, Color.Transparent)
                        )
                    )
            )
        }
    }
}

// === CUSTOM COMPONENT: Glass Input Field ===
@Composable
fun GlassInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(
            buildAnnotatedString {
                append(label)
                withStyle(style = SpanStyle(color = Color(0xFFEF4444))) { append(" *") }
            },
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.Gray) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = LoginAccentBlue) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = LoginAccentBlue,
                unfocusedBorderColor = LoginBorderSubtle,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = LoginAccentBlue
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if(label == "Refer Code") KeyboardCapitalization.Characters else KeyboardCapitalization.None
            ),
            singleLine = true
        )
    }
}