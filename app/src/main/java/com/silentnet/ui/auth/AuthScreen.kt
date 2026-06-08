package com.silentnet.ui.auth

import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silentnet.app.AppGraph
import com.silentnet.auth.AuthResult
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    graph: AppGraph,
    onLoggedIn: () -> Unit
) {
    var loginMode by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SilentNet Suite",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Privacy-first offline communication and emergency networking.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { loginMode = true },
                            modifier = Modifier.weight(1f),
                            colors = if (loginMode) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Login") }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { loginMode = false },
                            modifier = Modifier.weight(1f),
                            colors = if (!loginMode) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Register") }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!loginMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full name (required)") },
                            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("Nickname (optional)") },
                            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (!loginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm password") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    ElevatedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                val result = if (loginMode) {
                                    graph.authRepository.login(username, password)
                                } else {
                                    if (password != confirmPassword) {
                                        AuthResult.Error("Passwords do not match")
                                    } else if (fullName.isBlank()) {
                                        AuthResult.Error("Full name is required")
                                    } else {
                                        graph.authRepository.register(username, fullName, nickname.takeIf { it.isNotBlank() }, password)
                                    }
                                }
                                busy = false
                                when (result) {
                                    is AuthResult.Success -> onLoggedIn()
                                    is AuthResult.Error -> snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (loginMode) "Sign In" else "Create Account")
                        }
                    }

                    Text(
                        text = "Your credentials are hashed locally using SHA-256 for maximum security.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
