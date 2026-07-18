package com.walletapp.android.transactions.ui

import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Selector de fecha estándar de Material3 (research.md #2) en vez de texto libre — garantiza el
// formato YYYY-MM-DD que espera el backend sin depender de que el usuario lo escriba bien.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(label: String, date: String?, onDateSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Button(onClick = { showDialog = true }) {
        Text(date ?: label)
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(localDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                    showDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
