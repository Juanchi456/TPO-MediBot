package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.RobotProfile
import com.medibot.g1.data.model.TelemetryState
import com.medibot.g1.ui.components.ConfirmActionDialog
import com.medibot.g1.ui.theme.*

/**
 * Selección de robot y gestión de la conexión de la consola (vistas "selección del robot"
 * y "desconexión" de la consigna).
 */
@Composable
fun RobotSelectionScreen(
    fleet: List<RobotProfile>,
    connectedRobot: RobotProfile?,
    telemetry: TelemetryState,
    linkStatus: ConnectionStatus,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by rememberSaveable {
        mutableStateOf(connectedRobot?.robotId ?: fleet.firstOrNull { it.isAvailable }?.robotId)
    }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }
    val selected = fleet.firstOrNull { it.robotId == selectedId }
    val isSelectedConnected = selected != null && selected.robotId == connectedRobot?.robotId

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "MEDIBOT G1",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonCyan,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Logística hospitalaria autónoma • Selección de robot",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConnectionSummary(connectedRobot = connectedRobot, linkStatus = linkStatus)

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel("FLOTA DEL HOSPITAL (${fleet.size} ROBOTS)")
        Spacer(modifier = Modifier.height(8.dp))

        fleet.forEach { profile ->
            FleetItem(
                profile = profile,
                isSelected = profile.robotId == selectedId,
                isConnected = profile.robotId == connectedRobot?.robotId,
                onSelect = { selectedId = profile.robotId }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selected != null) {
            Spacer(modifier = Modifier.height(8.dp))
            RobotDetailCard(
                profile = selected,
                isConnected = isSelectedConnected,
                otherRobotConnected = connectedRobot != null && !isSelectedConnected,
                telemetry = telemetry,
                onConnect = { onConnect(selected.robotId) },
                onOpenConsole = onOpenConsole,
                onDisconnect = { showDisconnectConfirmation = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        InfrastructureCard()

        // Espacio para que el botón flotante del panel de ingeniería no tape controles
        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showDisconnectConfirmation) {
        val missionName = telemetry.currentMission?.name
        ConfirmActionDialog(
            title = "¿Desconectar la consola?",
            message = if (missionName != null) {
                "Hay una misión en curso ($missionName). El robot la continuará de forma autónoma, pero nadie lo supervisará ni podrá detenerlo desde esta consola."
            } else {
                "Dejará de recibir telemetría y no podrá emitir órdenes hasta volver a conectarse."
            },
            confirmLabel = "DESCONECTAR",
            accentColor = WarningAmber,
            onConfirm = {
                showDisconnectConfirmation = false
                onDisconnect()
            },
            onDismiss = { showDisconnectConfirmation = false }
        )
    }
}

@Composable
private fun ConnectionSummary(connectedRobot: RobotProfile?, linkStatus: ConnectionStatus) {
    val connected = connectedRobot != null
    val color = when {
        !connected -> StaleGray
        linkStatus == ConnectionStatus.ONLINE -> TerminalGreen
        linkStatus == ConnectionStatus.DEGRADED -> WarningAmber
        else -> CriticalRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (connected) Icons.Default.Link else Icons.Default.LinkOff,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (connectedRobot != null) {
                "Consola conectada a ${connectedRobot.displayName} · enlace ${linkStatus.name}"
            } else {
                "Consola desconectada: seleccione un robot disponible y conéctese."
            },
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun FleetItem(
    profile: RobotProfile,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit
) {
    val statusColor = when {
        isConnected -> TerminalGreen
        profile.isAvailable -> NeonCyan
        else -> StaleGray
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) NeonCyan else CardNormalBorder,
                RoundedCornerShape(12.dp)
            )
            .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
            .padding(12.dp)
            .alpha(if (profile.isAvailable) 1f else 0.6f)
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${profile.displayName} · ${profile.robotId}",
                style = MaterialTheme.typography.titleMedium,
                color = DarkOnBackground,
                fontWeight = FontWeight.Bold
            )
            Text(text = profile.homeLocation, fontSize = 12.sp, color = DarkOnSurface)
            Text(
                text = if (isConnected) "Conectado a esta consola" else profile.statusNote,
                fontSize = 12.sp,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = DarkOnSurface)
        )
    }
}

@Composable
private fun RobotDetailCard(
    profile: RobotProfile,
    isConnected: Boolean,
    otherRobotConnected: Boolean,
    telemetry: TelemetryState,
    onConnect: () -> Unit,
    onOpenConsole: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${profile.model.uppercase()} · ${profile.robotId}",
                style = MaterialTheme.typography.titleMedium,
                color = DarkOnBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel("SUBSISTEMAS EMBARCADOS")
            Spacer(modifier = Modifier.height(6.dp))
            TechSpecRow(Icons.Default.Radar, "LiDAR", "Livox Mid-360 (360° × 59°)")
            TechSpecRow(Icons.Default.Visibility, "Cámara de profundidad", "Intel RealSense D435i")
            TechSpecRow(Icons.Default.PanTool, "Manos", "Unitree Dex3-1 (equipamiento adicional)")
            TechSpecRow(Icons.Default.AcUnit, "Cápsula médica", "Peltier activa 2–6 °C (desarrollo propio)")
            TechSpecRow(Icons.AutoMirrored.Filled.DirectionsWalk, "Locomoción", "Bípedo, ~35 kg, 0.8 m/s máx. en hospital")

            if (isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(
                        label = "BATERÍA",
                        value = "${telemetry.batteryPercentage}%",
                        color = if (telemetry.batteryPercentage >= 20) TerminalGreen else WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "ESTADO",
                        value = telemetry.operationalState.displayName,
                        color = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                !profile.isAvailable -> Text(
                    text = "No disponible: ${profile.statusNote}. No se puede conectar.",
                    color = WarningAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                isConnected -> {
                    Button(
                        onClick = onOpenConsole,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ABRIR PANEL DE SUPERVISIÓN", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DESCONECTAR CONSOLA", fontWeight = FontWeight.Bold)
                    }
                }
                otherRobotConnected -> Text(
                    text = "Desconecte primero el robot actual para conectarse a este.",
                    color = WarningAmber,
                    fontSize = 13.sp
                )
                else -> Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONECTAR CONSOLA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfrastructureCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, CardNormalBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            SectionLabel("INFRAESTRUCTURA DEL RECORRIDO")
            Spacer(modifier = Modifier.height(8.dp))
            TechSpecRow(Icons.Default.Elevator, "Ascensor", "Botonera física a 1.15 m (sin integración IoT)")
            TechSpecRow(Icons.Default.DoorSliding, "Puerta cortafuego", "Picaporte a 1.00 m, apertura con brazo")
            TechSpecRow(Icons.Default.Nfc, "Puntos NFC", "Lab Central (PB) y Enfermería UTI (P2)")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DarkOnSurface,
        letterSpacing = 1.sp
    )
}

@Composable
private fun TechSpecRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonCyan.copy(alpha = 0.8f),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            color = DarkOnSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = DarkOnBackground,
            fontWeight = FontWeight.SemiBold
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
            Text(text = label, fontSize = 10.sp, color = DarkOnSurface, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
