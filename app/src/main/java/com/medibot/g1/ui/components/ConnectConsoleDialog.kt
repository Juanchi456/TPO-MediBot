package com.medibot.g1.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.ConsoleRole
import com.medibot.g1.ui.theme.*

/**
 * Identificación al conectar la consola: ID o legajo del operador (sin contraseña en el MVP) y rol.
 * [onConfirm] devuelve false si el ViewModel rechaza los datos.
 */
@Composable
fun ConnectConsoleDialog(
    robotName: String,
    onConfirm: (operatorId: String, role: ConsoleRole) -> Boolean,
    onDismiss: () -> Unit
) {
    var operatorId by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(ConsoleRole.OPERATOR) }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conectar a $robotName", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = operatorId,
                    onValueChange = {
                        operatorId = it
                        showError = false
                    },
                    label = { Text("ID de operador / legajo") },
                    placeholder = { Text("Ej.: OP-LAB-04") },
                    singleLine = true,
                    isError = showError,
                    supportingText = {
                        Text(
                            if (showError) "ID inválido: de 3 a 20 letras, números o guiones."
                            else "Queda registrado en la auditoría de cada orden."
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnBackground,
                        unfocusedTextColor = DarkOnBackground,
                        focusedBorderColor = NeonCyan,
                        focusedLabelColor = NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rol", color = DarkOnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ConsoleRole.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = role == option, onClick = { role = option }, role = Role.RadioButton)
                    ) {
                        RadioButton(
                            selected = role == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = DarkOnSurface)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(option.displayName, color = DarkOnBackground, fontWeight = FontWeight.Bold)
                            Text(option.description, color = DarkOnSurface, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!onConfirm(operatorId, role)) showError = true },
                enabled = operatorId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
            ) {
                Text("CONECTAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkOnSurface)
            ) {
                Text("DESCARTAR")
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
