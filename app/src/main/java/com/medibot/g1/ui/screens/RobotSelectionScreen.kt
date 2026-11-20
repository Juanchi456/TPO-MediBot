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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.TelemetryState
import com.medibot.g1.ui.theme.*

@Composable
fun RobotSelectionScreen(
    telemetry: TelemetryState,
    linkStatus: ConnectionStatus,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App / Hospital Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MEDIBOT G1",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Logística Hospitalaria Autónoma • HMI/HRI",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface
                )
            }

            // Unitree G1 Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricViolet.copy(alpha = 0.2f))
                    .border(1.dp, ElectricViolet, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "UNITREE G1 EDU",
                    color = ElectricViolet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Robot Technical Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header inside Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = telemetry.robotId,
                                style = MaterialTheme.typography.titleLarge,
                                color = DarkOnBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Firmware: ${telemetry.firmwareVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurface
                            )
                        }
                    }

                    // Online indicator
                    val statusColor = when (linkStatus) {
                        ConnectionStatus.ONLINE -> TerminalGreen
                        ConnectionStatus.DEGRADED -> WarningAmber
                        ConnectionStatus.OFFLINE -> CriticalRed
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = linkStatus.name,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                HorizontalDivider(color = CardNormalBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SUBSISTEMAS ROBÓTICOS EMBARCADOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurface,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Hardware Specifications Grid
                TechSpecRow(icon = Icons.Default.Radar, label = "LiDAR Primario", value = "Livox Mid-360 (FOV 360° x 59°)")
                TechSpecRow(icon = Icons.Default.Visibility, label = "Cámaras RGB-D", value = "Intel RealSense D435i (Estéreo + IMU)")
                TechSpecRow(icon = Icons.Default.PanTool, label = "Manipuladores", value = "Unitree Dex3-1 (Manos diestras con feedback)")
                TechSpecRow(icon = Icons.Default.AcUnit, label = "Cápsula Médica", value = "Módulo Peltier Activo (2.0°C - 6.0°C)")
                TechSpecRow(icon = Icons.AutoMirrored.Filled.DirectionsWalk, label = "Cinemática Bípeda", value = "23 DOF, actuadores 120Nm, 35kg")

                Spacer(modifier = Modifier.height(18.dp))

                // Current Telemetry Snapshot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStat(
                        label = "BATERÍA",
                        value = "${telemetry.batteryPercentage}%",
                        color = if (telemetry.batteryPercentage > 20) TerminalGreen else WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "LATENCIA",
                        value = "${telemetry.latencyMs} ms",
                        color = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "TEMPERATURA",
                        value = "${telemetry.servicePayload.compartmentTempCelsius}°C",
                        color = if (telemetry.servicePayload.compartmentTempCelsius <= 6.0f) TerminalGreen else CriticalRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Button(
                    onClick = onNavigateToDashboard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ABRIR CONSOLA DE SUPERVISIÓN HMI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Hospital Area Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, CardNormalBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "ESTADO DE INFRAESTRUCTURA HOSPITALARIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TechSpecRow(icon = Icons.Default.Elevator, label = "Ascensor Médico", value = "Enlace BACnet/IP Operativo (PB ↔ P2)")
                TechSpecRow(icon = Icons.Default.DoorSliding, label = "Puertas Cortafuego", value = "Interlock RFID habilitado")
                TechSpecRow(icon = Icons.Default.Nfc, label = "Puntos NFC", value = "Lab Central (PB) y Enfermería UTI (P2)")
            }
        }
    }
}

@Composable
private fun TechSpecRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonCyan.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            fontSize = 11.sp,
            color = DarkOnSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = DarkOnBackground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, CardNormalBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = DarkOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

