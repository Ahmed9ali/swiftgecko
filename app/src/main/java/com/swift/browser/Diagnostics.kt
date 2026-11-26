package com.swift.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagnosticsDialog(title: String, text: String, host: UiHost?, onClose: () -> Unit, onClear: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text.ifBlank { "(empty)" }, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { host?.copy(text) }) { Text("Copy") }
                TextButton(onClick = { host?.share(text.takeLast(40000)) }) { Text("Share") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = { if (onClear != null) TextButton(onClick = onClear) { Text("Clear") } },
    )
}
