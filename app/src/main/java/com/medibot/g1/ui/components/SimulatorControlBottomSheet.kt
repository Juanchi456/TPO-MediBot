package com.medibot.g1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.repository.SimulationScenario
import com.medibot.g1.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorControlBottomSheet(
    linkUp: Boolean,
    forceRejectNext: Boolean,
    onInjectScenario: (SimulationScenario) -> Unit,
    onToggleForceReject: (Boolean) -> Unit,
    onRestoreConnection: () -> Unit,
    onResetSimulation: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = DarkOnSurface.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = ElectricViolet,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "INYECTOR DE ESCENARIOS DE SIMULACIÓN",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Panel de Pruebas Unitree G1 EDU (Modo Ingeniero/Debug)",
                        fontSize = 11.sp,
                        color = DarkOnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch: Forzar Rechazo del Próximo Comando
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, if (forceRejectNext) WarningAmber else CardNormalBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FORZAR RECHAZO DEL PRÓXIMO COMANDO",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (forceRejectNext) WarningAmber else DarkOnBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Demuestra el estado REJECTED simulando una política de seguridad activa.",
                            fontSize = 11.sp,
                            color = DarkOnSurface
                        )
                    }
                    Switch(
                        checked = forceRejectNext,
                        onCheckedChange = onToggleForceReject,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarningAmber,
                            checkedTrackColor = WarningAmber.copy(alpha = 0.3f),
                            uncheckedThumbColor = DarkOnSurface,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Restablecer Enlace (si el enlace simulado está caído)
            if (!linkUp) {
                Button(
                    onClick = {
                        onRestoreConnection()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalGreen,
                        contentColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RESTABLECER ENLACE WI-FI 6", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            OutlinedButton(
                onClick = {
                    onResetSimulation()
                    onDismissRequest()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkOnBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("REINICIAR SIMULACIÓN (ROBOT EN BASE)", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Vuelve al estado inicial: sirve para salir de estados sin recuperación definida (agachado, detención, tamper).",
                fontSize = 11.sp,
                color = DarkOnSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "5 ESCENARIOS OBLIGATORIOS:",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurface,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scenario 1
            ScenarioButton(
                number = 1,
                title = "Misión Normal (Lab → UTI)",
                desc = "Batería 85%, cápsula a 4.0 °C. Recorre Lab → Cortafuego → Ascensor → P2 → UTI, valida NFC y registra COMPLETADA en Room.",
                color = TerminalGreen,
                icon = Icons.Default.CheckCircle,
                onClick = {
                    onInjectScenario(SimulationScenario.NORMAL_MISSION)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scenario 2
            ScenarioButton(
                number = 2,
                title = "Batería Baja (14% → < 8%)",
                desc = "Durante una misión cae a 14% (alerta WARNING) y se descarga rápido. Acción: regresar a punto seguro. Si baja de 8%, se agacha (CROUCHED_SAFE_LOCK) y la misión queda FALLIDA.",
                color = WarningAmber,
                icon = Icons.Default.BatteryAlert,
                onClick = {
                    onInjectScenario(SimulationScenario.LOW_BATTERY)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scenario 3
            ScenarioButton(
                number = 3,
                title = "Desconexión Wi-Fi 6 (Stale Telemetry)",
                desc = "Corta el enlace: el robot sigue operando pero la app deja de recibir datos. A los 4 s se marcan como antiguos; las órdenes quedan SIN RESPUESTA a los 2,5 s.",
                color = CriticalRed,
                icon = Icons.Default.SignalWifiConnectedNoInternet4,
                onClick = {
                    onInjectScenario(SimulationScenario.DISCONNECTION)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scenario 4
            ScenarioButton(
                number = 4,
                title = "Incidente en Pasillo (Camilla a 0.35m)",
                desc = "Frena en la puerta cortafuego (INCIDENT_BLOCKED). Reconocer la alerta habilita Reanudar; el robot lo rechaza hasta que el pasillo se despeja (~10 s). Alternativa: desviar a punto seguro.",
                color = CriticalRed,
                icon = Icons.Default.Warning,
                onClick = {
                    onInjectScenario(SimulationScenario.INCIDENT)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scenario 5
            ScenarioButton(
                number = 5,
                title = "Cadena de Frío / Tamper (8.4°C)",
                desc = "Durante una misión la cápsula sube a 8.4 °C y se activa TAMPER_ALERT. Exige ACK y cancelar o desviar. Se sale con Reiniciar simulación.",
                color = ElectricViolet,
                icon = Icons.Default.Thermostat,
                onClick = {
                    onInjectScenario(SimulationScenario.COLD_CHAIN_FAILURE)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScenarioButton(
    number: Int,
    title: String,
    desc: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E$number: $title",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface,
                    lineHeight = 15.sp,
                    fontSize = 11.sp
                )
            }
        }
    }
}

