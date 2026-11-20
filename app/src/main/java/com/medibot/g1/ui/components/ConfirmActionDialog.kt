package com.medibot.g1.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibot.g1.ui.theme.DarkOnBackground
import com.medibot.g1.ui.theme.DarkOnSurface
import com.medibot.g1.ui.theme.DarkSurface

/**
 * Confirmación para acciones con consecuencias (riesgo 1 del informe: órdenes accidentales).
 * El botón de confirmar dice qué hace, no "Aceptar".
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    accentColor: Color,
    dismissLabel: String = "DESCARTAR",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = accentColor, fontWeight = FontWeight.Bold) },
        text = { Text(text = message, color = DarkOnBackground) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkOnSurface)
            ) {
                Text(dismissLabel)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
