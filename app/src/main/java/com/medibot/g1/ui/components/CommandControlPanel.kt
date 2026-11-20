package com.medibot.g1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.domain.CommandAvailability
import com.medibot.g1.ui.theme.*

private enum class PendingConfirmation { CANCEL, RETURN }

@Composable
fun CommandControlPanel(
    availability: CommandAvailability,
    commandState: CommandExecutionState,
    onSendCommand: (RobotCommand) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmation by rememberSaveable { mutableStateOf<PendingConfirmation?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, CardNormalBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONSOLA DE CONTROL OPERATIVO",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkOnBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SafetyDisclaimer()

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(
                visible = commandState !is CommandExecutionState.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CommandFeedbackBanner(commandState = commandState, onDismiss = onDismissFeedback)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledCommandButton(
                        label = "PAUSAR",
                        icon = Icons.Default.Pause,
                        enabled = availability.pause,
                        color = WarningAmber,
                        onClick = { onSendCommand(RobotCommand.PauseMission) },
                        modifier = Modifier.weight(1f)
                    )
                    FilledCommandButton(
                        label = "REANUDAR",
                        icon = Icons.Default.PlayArrow,
                        enabled = availability.resume,
                        color = TerminalGreen,
                        onClick = { onSendCommand(RobotCommand.ResumeMission) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCommandButton(
                        label = "A PTO. SEGURO",
                        icon = Icons.Default.Home,
                        enabled = availability.returnToSafePoint,
                        color = NeonCyan,
                        onClick = { confirmation = PendingConfirmation.RETURN },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedCommandButton(
                        label = "CANCELAR",
                        icon = Icons.Default.Close,
                        enabled = availability.cancel,
                        color = CriticalRed,
                        onClick = { confirmation = PendingConfirmation.CANCEL },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    when (confirmation) {
        PendingConfirmation.CANCEL -> ConfirmActionDialog(
            title = "¿Cancelar la misión?",
            message = "El traslado se aborta y la carga no se entrega. La misión quedará registrada como CANCELADA.",
            confirmLabel = "CANCELAR MISIÓN",
            accentColor = CriticalRed,
            onConfirm = {
                confirmation = null
                onSendCommand(RobotCommand.CancelMission)
            },
            onDismiss = { confirmation = null }
        )
        PendingConfirmation.RETURN -> ConfirmActionDialog(
            title = "¿Regresar a punto seguro?",
            message = "El robot interrumpe la ruta y se dirige al punto seguro. Si hay una misión activa, la carga no se entrega.",
            confirmLabel = "REGRESAR",
            accentColor = WarningAmber,
            onConfirm = {
                confirmation = null
                onSendCommand(RobotCommand.ReturnToSafePoint)
            },
            onDismiss = { confirmation = null }
        )
        null -> Unit
    }
}

@Composable
private fun SafetyDisclaimer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WarningAmber.copy(alpha = 0.1f))
            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AVISO DE SEGURIDAD: las órdenes son comandos de software. No reemplazan la parada física de emergencia del Unitree G1. Una orden enviada no equivale a una orden ejecutada.",
                fontSize = 12.sp,
                color = DarkOnSurface,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun CommandFeedbackBanner(commandState: CommandExecutionState, onDismiss: () -> Unit) {
    val color = when (commandState) {
        is CommandExecutionState.Pending -> NeonCyan
        is CommandExecutionState.Confirmed -> TerminalGreen
        is CommandExecutionState.Rejected -> WarningAmber
        is CommandExecutionState.NoResponse -> CriticalRed
        CommandExecutionState.Idle -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (commandState) {
                is CommandExecutionState.Pending -> CircularProgressIndicator(
                    color = color,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                is CommandExecutionState.Confirmed -> FeedbackIcon(Icons.Default.CheckCircle, color)
                is CommandExecutionState.Rejected -> FeedbackIcon(Icons.Default.Cancel, color)
                is CommandExecutionState.NoResponse -> FeedbackIcon(Icons.Default.SignalWifiConnectedNoInternet4, color)
                CommandExecutionState.Idle -> Unit
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when (commandState) {
                    is CommandExecutionState.Pending ->
                        "PENDIENTE: ${commandState.commandName} enviada, esperando confirmación del robot…"
                    is CommandExecutionState.Confirmed ->
                        "CONFIRMADA: ${commandState.message}"
                    is CommandExecutionState.Rejected ->
                        "RECHAZADA: ${commandState.reason}"
                    is CommandExecutionState.NoResponse ->
                        "SIN RESPUESTA: ${commandState.commandName} no fue confirmada en ${commandState.timeoutMs} ms. No se sabe si el robot la ejecutó: verifique la telemetría."
                    CommandExecutionState.Idle -> ""
                },
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            if (commandState !is CommandExecutionState.Pending) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar resultado de la orden",
                        tint = DarkOnSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun FeedbackIcon(icon: ImageVector, color: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
}

@Composable
private fun FilledCommandButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = DarkBackground,
            disabledContainerColor = DarkSurfaceVariant,
            disabledContentColor = StaleGray
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun OutlinedCommandButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = StaleGray
        ),
        border = BorderStroke(1.dp, if (enabled) color else CardStaleBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
