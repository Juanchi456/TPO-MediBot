package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.*
import com.medibot.g1.ui.components.*
import com.medibot.g1.ui.theme.*
import com.medibot.g1.ui.viewmodel.SupervisionUiState
import com.medibot.g1.domain.OperationalLimits
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: SupervisionUiState,
    onSendCommand: (RobotCommand) -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onDismissCommandFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmergencyConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val telemetry = uiState.telemetry
    val isStale = uiState.isTelemetryStale
    val staleSeconds = uiState.secondsSinceUpdate
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // TOP HEADER: Connectivity, Stale Badge, Battery & Stop Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, if (isStale) CardStaleBorder else CardNormalBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connectivity Pill
                    val statusColor = when (uiState.linkStatus) {
                        ConnectionStatus.ONLINE -> TerminalGreen
                        ConnectionStatus.DEGRADED -> WarningAmber
                        ConnectionStatus.OFFLINE -> CriticalRed
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isStale) {
                                "SIN ENLACE"
                            } else {
                                "${uiState.linkStatus.name} (${telemetry.latencyMs}ms / ${telemetry.signalDbm}dBm)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Stale Badge (if applicable)
                    if (isStale) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarningAmber.copy(alpha = 0.2f))
                                .border(1.dp, WarningAmber, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DATOS ANTIGUOS (hace ${staleSeconds}s)",
                                color = WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Operative Stop Permanent Button
                    Button(
                        onClick = { showEmergencyConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CriticalRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DETENCIÓN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Última actualización: siempre visible (no solo cuando el dato es antiguo)
                Text(
                    text = "Última actualización: ${timeFormat.format(Date(uiState.lastUpdateTimestamp))} · hace ${uiState.secondsSinceUpdate} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStale) WarningAmber else DarkOnSurface,
                    fontWeight = if (isStale) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Battery Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                telemetry.batteryPercentage > 60 -> Icons.Default.BatteryFull
                                telemetry.batteryPercentage > 20 -> Icons.Default.BatteryChargingFull
                                else -> Icons.Default.BatteryAlert
                            },
                            contentDescription = null,
                            tint = if (telemetry.batteryPercentage > 20) TerminalGreen else CriticalRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BATERÍA: ${telemetry.batteryPercentage}% (~${telemetry.estimatedMinutesLeft} min)",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "TEMP PACK: ${telemetry.batteryTempCelsius}°C",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Alerts Banner
        AlertBanner(
            alerts = telemetry.activeAlerts,
            canAcknowledge = uiState.availability.acknowledge,
            onAcknowledgeAlert = onAcknowledgeAlert,
            isStale = isStale
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2D Tactical Hospital Map
        HospitalMapCanvas(
            currentNode = telemetry.currentNode,
            progressPct = telemetry.missionProgressPct,
            operationalState = telemetry.operationalState,
            missionName = telemetry.currentMission?.name,
            isStale = isStale
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Medical Payload Section Header
        Text(
            text = "TELEMETRÍA CRÍTICA DE CARGA MÉDICA",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurface,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3 Specific Medical Variables Grid
        val tempVal = telemetry.servicePayload.compartmentTempCelsius
        val tempState = when {
            tempVal > OperationalLimits.COLD_CHAIN_BREAK_TEMP_C -> TelemetryCardState.ERROR
            tempVal > OperationalLimits.MAX_START_TEMP_C -> TelemetryCardState.WARNING
            else -> TelemetryCardState.NORMAL
        }

        val stabVal = telemetry.servicePayload.bioSampleStabilityIndexG
        val stabState = when {
            stabVal > OperationalLimits.VIBRATION_CRITICAL_G -> TelemetryCardState.ERROR
            stabVal > OperationalLimits.VIBRATION_WARNING_G -> TelemetryCardState.WARNING
            else -> TelemetryCardState.NORMAL
        }

        val lockState = when (telemetry.servicePayload.smartLockState) {
            SmartLockState.SEALED_LOCKED -> TelemetryCardState.NORMAL
            SmartLockState.UNLOCKED_NFC_AUTHORIZED -> TelemetryCardState.WARNING
            SmartLockState.TAMPER_ALERT -> TelemetryCardState.ERROR
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                title = "Temperatura Cápsula",
                value = String.format("%.1f", tempVal),
                unit = "°C",
                subtitle = if (tempVal > OperationalLimits.COLD_CHAIN_BREAK_TEMP_C) "¡CADENA ROTA!" else "Rango seguro 2–6 °C",
                icon = Icons.Default.Thermostat,
                cardState = tempState,
                isStale = isStale,
                staleSecondsAgo = staleSeconds,
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                title = "Estabilidad Tubos",
                value = String.format("%.2f", stabVal),
                unit = "G",
                subtitle = if (stabVal > OperationalLimits.VIBRATION_CRITICAL_G) "¡RIESGO HEMÓLISIS!" else "Normal < 0.30 G",
                icon = Icons.Default.Vibration,
                cardState = stabState,
                isStale = isStale,
                staleSecondsAgo = staleSeconds,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Smart Lock Card (Full Width)
        TelemetryCard(
            title = "Cerradura Inteligente NFC",
            value = telemetry.servicePayload.smartLockState.displayName,
            subtitle = when (telemetry.servicePayload.smartLockState) {
                SmartLockState.SEALED_LOCKED -> "Custodia sellada por cerrojo solenoide"
                SmartLockState.UNLOCKED_NFC_AUTHORIZED -> "Apertura autorizada por tarjeta de enfermería"
                SmartLockState.TAMPER_ALERT -> "¡ALERTA DE MANIPULACIÓN NO AUTORIZADA!"
            },
            icon = when (telemetry.servicePayload.smartLockState) {
                SmartLockState.SEALED_LOCKED -> Icons.Default.Lock
                SmartLockState.UNLOCKED_NFC_AUTHORIZED -> Icons.Default.LockOpen
                SmartLockState.TAMPER_ALERT -> Icons.Default.Warning
            },
            cardState = lockState,
            isStale = isStale,
            staleSecondsAgo = staleSeconds
        )

        Spacer(modifier = Modifier.height(14.dp))

        // HRI and Kinematics Header
        Text(
            text = "ESTADO CINEMÁTICO E INTERACCIÓN HRI",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurface,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // HRI Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                title = "Estado Operativo",
                value = telemetry.operationalState.displayName,
                subtitle = "Postura: ${telemetry.posture.displayName}",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                cardState = when (telemetry.operationalState) {
                    OperationalState.INCIDENT_BLOCKED, OperationalState.EMERGENCY_STOPPED -> TelemetryCardState.ERROR
                    OperationalState.PAUSED, OperationalState.LOW_BATTERY_CROUCHED -> TelemetryCardState.WARNING
                    else -> TelemetryCardState.NORMAL
                },
                isStale = isStale,
                staleSecondsAgo = staleSeconds,
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                title = "Velocidad Bípeda",
                value = String.format("%.2f", telemetry.speedMps),
                unit = "m/s",
                subtitle = "Límite: ${OperationalLimits.MAX_SPEED_MPS} m/s",
                icon = Icons.Default.Speed,
                cardState = if (telemetry.speedMps > OperationalLimits.MAX_SPEED_MPS) TelemetryCardState.WARNING else TelemetryCardState.NORMAL,
                isStale = isStale,
                staleSecondsAgo = staleSeconds,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // HRI Voice and LED Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, if (isStale) CardStaleBorder else CardNormalBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .alpha(if (isStale) 0.55f else 1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LED Color indicator
                val ledColor = runCatching { Color(android.graphics.Color.parseColor(telemetry.hriLedColor)) }
                    .getOrDefault(NeonCyan)

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ledColor.copy(alpha = 0.25f))
                        .border(2.dp, ledColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = ledColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MENSAJE DE VOZ HRI (SINTETIZADOR EMBARCADO)",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurface,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"${telemetry.hriVoiceStatus}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Discrete Command Console
        CommandControlPanel(
            availability = uiState.availability,
            commandState = uiState.commandState,
            onSendCommand = onSendCommand,
            onDismissFeedback = onDismissCommandFeedback
        )

        // Espacio para que el botón flotante del panel de ingeniería no tape controles
        Spacer(modifier = Modifier.height(96.dp))
    }

    // Confirmación de la detención operativa (riesgo 1: órdenes accidentales)
    if (showEmergencyConfirmDialog) {
        ConfirmActionDialog(
            title = "CONFIRMAR DETENCIÓN OPERATIVA",
            message = "El robot frena de inmediato y queda inmóvil. Es un comando de software: no reemplaza la parada física de emergencia.",
            confirmLabel = "DETENER AHORA",
            accentColor = CriticalRed,
            onConfirm = {
                showEmergencyConfirmDialog = false
                onSendCommand(RobotCommand.OperationalStop)
            },
            onDismiss = { showEmergencyConfirmDialog = false }
        )
    }
}

