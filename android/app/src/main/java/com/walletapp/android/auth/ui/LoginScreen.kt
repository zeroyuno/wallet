package com.walletapp.android.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.auth.AuthViewModel
import com.walletapp.android.auth.LoginUiState

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val state by viewModel.loginState.collectAsState()

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) {
            onLoggedIn()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Iniciar sesión", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        val errorState = state
        if (errorState is LoginUiState.Error) {
            Text(text = errorState.message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.login(email, password) },
            enabled = state !is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state is LoginUiState.Loading) "Ingresando..." else "Iniciar sesión")
        }

        TextButton(onClick = onNavigateToRegister) {
            Text("¿No tienes cuenta? Regístrate")
        }
    }
}
