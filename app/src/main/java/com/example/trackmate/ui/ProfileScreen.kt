package com.example.trackmate.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// === 🎨 UNIQUE COLORS FOR PROFILE ===
private val ProfileDarkBg = Color(0xFF0B0E14)
private val ProfileBlue = Color(0xFF3B82F6)
private val ProfilePurple = Color(0xFF9333EA)
private val ProfileRed = Color(0xFFEF4444)
private val ProfileWhite = Color(0xFFFFFFFF)
private val ProfileGray = Color(0xFF9CA3AF)

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(UserDataManager.getUserName() ?: "") }
    var phone by remember { mutableStateOf(UserDataManager.getUserPhone() ?: "") }
    var email by remember { mutableStateOf(UserDataManager.getUserEmail() ?: "") }

    val isExistingUser = remember { !UserDataManager.getUserName().isNullOrBlank() }
    val screenTitle = if (isExistingUser) "My Profile" else "Create Profile"

    Scaffold(
        containerColor = ProfileDarkBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = ProfileWhite)
                }
                Text(screenTitle, color = ProfileWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ProfileBlue, ProfilePurple))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfileWhite
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            DarkTextField(label = "Full Name", icon = Icons.Default.Person, value = name, onValueChange = { name = it }, readOnly = isExistingUser)
            Spacer(modifier = Modifier.height(16.dp))
            DarkTextField(label = "Phone Number", icon = Icons.Default.Phone, value = phone, onValueChange = { phone = it }, keyboardType = KeyboardType.Phone, readOnly = isExistingUser)
            Spacer(modifier = Modifier.height(16.dp))
            DarkTextField(label = "Email Address", icon = Icons.Default.Email, value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email, readOnly = false)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    UserDataManager.saveUser(name, phone, email)
                    scope.launch {
                        try {
                            val userMap = mapOf("name" to name, "email" to email, "status" to "Active")
                            SupabaseManager.client.from("users").insert(userMap)
                            Toast.makeText(context, "✅ Profile Updated!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfileBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Update Profile", fontSize = 18.sp, color = ProfileWhite)
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfileRed),
                border = BorderStroke(1.dp, ProfileRed),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.ExitToApp, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun DarkTextField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (!readOnly) onValueChange(it) },
        label = { Text(label, color = ProfileGray) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (readOnly) ProfileGray else ProfileBlue) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ProfileBlue,
            unfocusedBorderColor = ProfileGray,
            focusedTextColor = ProfileWhite,
            unfocusedTextColor = ProfileWhite,
            disabledTextColor = ProfileWhite,
            disabledBorderColor = Color.DarkGray,
            disabledContainerColor = Color(0xFF0F1216),
            disabledLeadingIconColor = ProfileGray,
            cursorColor = ProfileBlue,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        enabled = !readOnly
    )
}