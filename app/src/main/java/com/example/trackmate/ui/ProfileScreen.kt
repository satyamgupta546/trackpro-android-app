package com.example.trackmate.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke // <--- FIXED: Added missing import
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackmate.SupabaseClient
import com.example.trackmate.UserDataManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// === UNIQUE COLORS FOR PROFILE ===
private val ProfileDarkBg = Color(0xFF020817)
private val ProfileCardBg = Color(0xFF1E293B)
private val ProfileElectricBlue = Color(0xFF3B82F6)
private val ProfileTextWhite = Color(0xFFF8FAFC)
private val ProfileTextMuted = Color(0xFF94A3B8)
private val ProfileDangerRed = Color(0xFFEF4444)

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for Password Change Dialog
    var showPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // User Data
    val name = UserDataManager.getUserName() ?: "User"
    val phone = UserDataManager.getUserPhone() ?: "No Phone"
    val email = UserDataManager.getUserEmail() ?: "No Email"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileDarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // === MOVED DOWN ===
        Spacer(modifier = Modifier.height(60.dp))

        // === AVATAR ===
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ProfileElectricBlue, Color(0xFF6366F1)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = 40.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === NAME & INFO ===
        Text(text = name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ProfileTextWhite)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = phone, fontSize = 16.sp, color = ProfileTextMuted)
        Text(text = email, fontSize = 14.sp, color = ProfileTextMuted)

        Spacer(modifier = Modifier.height(48.dp))

        // === ACTION BUTTONS ===

        // 1. Change Password Button
        ProfileButton(
            text = "Change Password",
            icon = Icons.Outlined.Lock,
            color = ProfileElectricBlue,
            onClick = { showPasswordDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Logout Button
        ProfileButton(
            text = "Logout",
            icon = Icons.Outlined.Logout,
            color = ProfileDangerRed,
            onClick = {
                UserDataManager.logout()
                onLogout()
            }
        )
    }

    // === PASSWORD CHANGE DIALOG ===
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            containerColor = ProfileCardBg,
            title = { Text("New Password", color = ProfileTextWhite) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password", color = ProfileTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ProfileTextWhite,
                            unfocusedTextColor = ProfileTextWhite,
                            focusedBorderColor = ProfileElectricBlue,
                            unfocusedBorderColor = ProfileTextMuted
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password", color = ProfileTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ProfileTextWhite,
                            unfocusedTextColor = ProfileTextWhite,
                            focusedBorderColor = ProfileElectricBlue,
                            unfocusedBorderColor = ProfileTextMuted
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            Toast.makeText(context, "Password too short (min 6)", Toast.LENGTH_SHORT).show()
                        } else if (newPassword != confirmPassword) {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        } else {
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // Update Supabase
                                    SupabaseClient.client.from("app_users").update(
                                        mapOf("password" to newPassword)
                                    ) {
                                        filter { eq("id", UserDataManager.currentUserId) }
                                    }

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Password Updated!", Toast.LENGTH_SHORT).show()
                                        showPasswordDialog = false
                                        newPassword = ""
                                        confirmPassword = ""
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProfileElectricBlue),
                    enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel", color = ProfileTextMuted)
                }
            }
        )
    }
}

@Composable
fun ProfileButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}