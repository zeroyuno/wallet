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
import com.walletapp.android.auth.RegisterUiState

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    val state by viewModel.registerState.collectAsState()

    LaunchedEffect(state) {
        if (state is RegisterUiState.Success) {
            onRegistered()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Crear cuenta", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )
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
        if (errorState is RegisterUiState.Error) {
            Text(text = errorState.message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.register(email, password, displayName) },
            enabled = state !is RegisterUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state is RegisterUiState.Loading) "Creando..." else "Crear cuenta")
        }
    }
}
