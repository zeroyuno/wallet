package com.walletapp.android.transactions.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Selector de fecha estándar de Material3 (research.md #2) en vez de texto libre — garantiza el
// formato YYYY-MM-DD que espera el backend sin depender de que el usuario lo escriba bien. El
// modifier lo controla quien lo usa: en el formulario ocupa todo el ancho, en el filtro de fecha
// de la lista queda al tamaño de su contenido (dos campos Desde/Hasta uno al lado del otro).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(label: String, date: String?, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = date ?: label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (showDialog) {
        // Se arranca con la fecha ya seleccionada (si hay) en vez de siempre el día de hoy — se
        // recalcula cada vez que se reabre el diálogo porque este bloque completo entra y sale de
        // composición junto con showDialog.
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date?.toEpochMillisUtc())
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
                Row {
                    TextButton(onClick = {
                        onDateSelected(LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE))
                        showDialog = false
                    }) { Text("Hoy") }
                    TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun String.toEpochMillisUtc(): Long =
    LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
