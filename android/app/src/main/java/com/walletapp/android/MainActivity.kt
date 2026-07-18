package com.walletapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.walletapp.android.auth.ui.LoginScreen
import com.walletapp.android.auth.ui.RegisterScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        WalletApp()
                    }
                }
            }
        }
    }
}

private enum class Screen { LOGIN, REGISTER, HOME }

@Composable
private fun WalletApp() {
    var screen by remember { mutableStateOf(Screen.LOGIN) }

    when (screen) {
        Screen.LOGIN -> LoginScreen(
            onLoggedIn = { screen = Screen.HOME },
            onNavigateToRegister = { screen = Screen.REGISTER }
        )
        Screen.REGISTER -> RegisterScreen(
            onRegistered = { screen = Screen.LOGIN }
        )
        Screen.HOME -> Text(text = "Wallet — sesión iniciada")
    }
}
