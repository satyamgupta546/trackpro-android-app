package com.example.trackmate.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.trackmate.SupabaseClient
import com.example.trackmate.UserDataManager
import com.example.trackmate.model.AppUser
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// === UNIQUE COLORS ===
private val LoginElectricBlue = Color(0xFF3B82F6)
private val LoginDarkBg = Color(0xFF1E293B)
private val LoginCardBg = Color(0xFF334155)
private val LoginTextWhite = Color(0xFFF8FAFC)
private val LoginDeepPurple = Color(0xFF6366F1)
private val LoginTextMuted = Color(0xFF94A3B8)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var authMode by remember { mutableStateOf("choose") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // === STATE VARIABLES ===
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(LoginDarkBg)) {
        // Gradient Glows
        Box(
            modifier = Modifier.align(Alignment.TopStart).offset((-50).dp, (-50).dp).size(300.dp)
                .background(Brush.radialGradient(listOf(LoginElectricBlue.copy(alpha = 0.2f), Color.Transparent)))
        )
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).offset(100.dp, 50.dp).size(300.dp)
                .background(Brush.radialGradient(listOf(LoginDeepPurple.copy(alpha = 0.15f), Color.Transparent)))
        )

        // === SCROLLABLE CONTAINER ===
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                // To keep centering logic but allow scrolling, we check if we need spacer
                Spacer(modifier = Modifier.height(32.dp))

                // Logo
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(LoginElectricBlue, LoginDeepPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Navigation, null, tint = LoginTextWhite, modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Header Texts with Dynamic Fonts
                Text("Location Tracker", style = MaterialTheme.typography.headlineLarge, color = LoginTextWhite)
                Text("Track your journeys in real-time", style = MaterialTheme.typography.bodyLarge, color = LoginTextMuted)

                Spacer(modifier = Modifier.height(40.dp))

                // Card
                AnimatedContent(targetState = authMode, label = "AuthAnimation") { mode ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LoginCardBg),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            when (mode) {
                                "choose" -> ChooseView({ authMode = "login" }, { authMode = "signup" })
                                "login" -> LoginView(
                                    phone = phone, onPhoneChange = { phone = it },
                                    password = password, onPasswordChange = { password = it },
                                    isLoading = isLoading,
                                    onBack = { authMode = "choose" },
                                    onSubmit = {
                                        if (phone.isBlank()) {
                                            Toast.makeText(context, "Enter phone number", Toast.LENGTH_SHORT).show()
                                            return@LoginView
                                        }
                                        isLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val user = SupabaseClient.client.from("app_users").select {
                                                    filter {
                                                        eq("phone", phone)
                                                        eq("password", password)
                                                    }
                                                }.decodeSingleOrNull<AppUser>()

                                                withContext(Dispatchers.Main) {
                                                    if (user != null) {
                                                        UserDataManager.saveUser(user.id ?: 0, user.name, user.phone, user.email)
                                                        onLoginSuccess()
                                                    } else {
                                                        Toast.makeText(context, "Invalid credentials", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                            } finally { isLoading = false }
                                        }
                                    }
                                )
                                "signup" -> SignupView(
                                    name = name, onNameChange = { name = it },
                                    phone = phone, onPhoneChange = { phone = it },
                                    email = email, onEmailChange = { email = it },
                                    password = password, onPasswordChange = { password = it },
                                    isLoading = isLoading,
                                    onBack = { authMode = "choose" },
                                    onSubmit = {
                                        if (name.isBlank() || phone.isBlank()) {
                                            Toast.makeText(context, "Fill required fields", Toast.LENGTH_SHORT).show()
                                            return@SignupView
                                        }
                                        isLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val newUser = AppUser(name = name, phone = phone, email = email, password = password)
                                                val savedUser = SupabaseClient.client.from("app_users").insert(newUser) { select() }.decodeSingle<AppUser>()

                                                withContext(Dispatchers.Main) {
                                                    savedUser.id?.let {
                                                        UserDataManager.saveUser(it, savedUser.name, savedUser.phone, savedUser.email)
                                                        onLoginSuccess()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    val errorMsg = e.message ?: ""
                                                    if (errorMsg.contains("duplicate key") || errorMsg.contains("unique constraint")) {
                                                        Toast.makeText(context, "This phone is already registered. Please Login.", Toast.LENGTH_LONG).show()
                                                        authMode = "login"
                                                    } else {
                                                        Toast.makeText(context, "Registration Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } finally { isLoading = false }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// === HELPER VIEWS (Updated with Dynamic Fonts) ===

@Composable
fun ChooseView(onLoginClick: () -> Unit, onSignupClick: () -> Unit) {
    Text("Welcome", style = MaterialTheme.typography.titleLarge, color = LoginTextWhite)
    Text("Choose how you'd like to continue", style = MaterialTheme.typography.bodyLarge, color = LoginTextMuted)

    Spacer(modifier = Modifier.height(24.dp))

    Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = LoginElectricBlue), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Default.Login, null, tint = LoginTextWhite); Spacer(Modifier.width(8.dp));
        Text("Login", style = MaterialTheme.typography.titleMedium, color = LoginTextWhite)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(onClick = onSignupClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = LoginElectricBlue), border = BorderStroke(1.dp, LoginElectricBlue.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp));
        Text("Sign Up", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun LoginView(phone: String, onPhoneChange: (String) -> Unit, password: String, onPasswordChange: (String) -> Unit, isLoading: Boolean, onBack: () -> Unit, onSubmit: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = LoginTextMuted) }
        Text("Login", style = MaterialTheme.typography.titleLarge, color = LoginTextWhite)
    }
    Spacer(modifier = Modifier.height(24.dp))
    AuthInputField(value = phone, onValueChange = onPhoneChange, label = "Phone Number", icon = Icons.Default.Phone, isNumber = true)
    Spacer(modifier = Modifier.height(16.dp))
    AuthInputField(value = password, onValueChange = onPasswordChange, label = "Password", icon = Icons.Default.Lock, isPassword = true)
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = LoginElectricBlue), shape = RoundedCornerShape(12.dp), enabled = !isLoading) {
        if (isLoading) CircularProgressIndicator(color = LoginTextWhite, modifier = Modifier.size(24.dp)) else Text("Login", style = MaterialTheme.typography.titleMedium, color = LoginTextWhite)
    }
}

@Composable
fun SignupView(name: String, onNameChange: (String) -> Unit, phone: String, onPhoneChange: (String) -> Unit, email: String, onEmailChange: (String) -> Unit, password: String, onPasswordChange: (String) -> Unit, isLoading: Boolean, onBack: () -> Unit, onSubmit: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = LoginTextMuted) }
        Text("Create Account", style = MaterialTheme.typography.titleLarge, color = LoginTextWhite)
    }
    Spacer(modifier = Modifier.height(24.dp))
    AuthInputField(value = name, onValueChange = onNameChange, label = "Full Name", icon = Icons.Default.Person)
    Spacer(modifier = Modifier.height(16.dp))
    AuthInputField(value = phone, onValueChange = onPhoneChange, label = "Phone Number", icon = Icons.Default.Phone, isNumber = true)
    Spacer(modifier = Modifier.height(16.dp))
    AuthInputField(value = email, onValueChange = onEmailChange, label = "Email", icon = Icons.Default.Email)
    Spacer(modifier = Modifier.height(16.dp))
    AuthInputField(value = password, onValueChange = onPasswordChange, label = "Password", icon = Icons.Default.Lock, isPassword = true)
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = LoginElectricBlue), shape = RoundedCornerShape(12.dp), enabled = !isLoading) {
        if (isLoading) CircularProgressIndicator(color = LoginTextWhite, modifier = Modifier.size(24.dp)) else Text("Create Account", style = MaterialTheme.typography.titleMedium, color = LoginTextWhite)
    }
}

// === HELPER INPUT FIELD ===
@Composable
fun AuthInputField(
    value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
    isPassword: Boolean = false, isNumber: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = LoginTextMuted) },
        leadingIcon = { Icon(icon, null, tint = LoginElectricBlue) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Phone else KeyboardType.Text),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = LoginElectricBlue,
            unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
            cursorColor = LoginElectricBlue,
            focusedTextColor = LoginTextWhite,
            unfocusedTextColor = LoginTextWhite,
            focusedLabelColor = LoginTextMuted,
            unfocusedLabelColor = LoginTextMuted
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge // Dynamic font for input
    )
}