package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.medibot.g1.ui.theme.*
import com.medibot.g1.domain.Precondition
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun MissionsScreen(
    catalog: List<MissionConfig>,
    preconditions: List<Precondition>,
    canStart: Boolean,
    onStartMission: (MissionConfig) -> Unit,
    modifier: Modifier = Modifier,
    readOnlyReason: String? = null
) {
    var selectedMissionIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedMission = catalog[selectedMissionIndex.coerceIn(0, catalog.lastIndex)]
    val allPreconditionsMet = preconditions.all { it.passed }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "CATÁLOGO DE MISIONES HOSPITALARIAS",
            style = MaterialTheme.typography.headlineMedium,
            color = DarkOnBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Seleccione un protocolo de logística intrahospitalaria para despacho autónomo.",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preconditions Checklist Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(
                    1.dp,
                    if (allPreconditionsMet) TerminalGreen.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.5f),
                    RoundedCornerShape(14.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHECKLIST DE PRE-CONDICIONES DE DESPACHO",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurface,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = if (allPreconditionsMet) "TODO LISTO" else "BLOQUEADO",
                        color = if (allPreconditionsMet) TerminalGreen else WarningAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                preconditions.forEach { precondition ->
                    PreconditionItem(
                        label = precondition.label,
                        statusText = precondition.currentValue,
                        passed = precondition.passed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Missions List
        Text(
            text = "MISIONES CONFIGURADAS (${catalog.size} DISPONIBLES):",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurface,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        catalog.forEachIndexed { index, mission ->
            val isSelected = index == selectedMissionIndex
            val priorityColor = when (mission.priority) {
                MissionPriority.URGENT -> CriticalRed
                MissionPriority.STANDARD -> NeonCyan
                MissionPriority.ROUTINE -> TerminalGreen
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) NeonCyan else CardNormalBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { selectedMissionIndex = index },
                        role = Role.RadioButton
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mission.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) NeonCyan else DarkOnBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(priorityColor.copy(alpha = 0.15f))
                                .border(1.dp, priorityColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = mission.priority.displayName.uppercase(),
                                color = priorityColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = mission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ruta: ${mission.origin.displayName} → ${mission.destination.displayName}",
                            fontSize = 11.sp,
                            color = NeonCyan.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "Cápsula ${mission.requiredTempRange.start.toInt()}–${mission.requiredTempRange.endInclusive.toInt()} °C · Vib. máx ${mission.maxVibrationG} G",
                            maxLines = 1,
                            fontSize = 10.sp,
                            color = DarkOnSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (readOnlyReason != null) {
            Text(
                text = readOnlyReason,
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Dispatch Button
        Button(
            onClick = { onStartMission(selectedMission) },
            enabled = canStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = DarkBackground,
                disabledContainerColor = DarkSurfaceVariant,
                disabledContentColor = StaleGray
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DESPACHAR: ${selectedMission.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Espacio para que el botón flotante del panel de ingeniería no tape controles
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun PreconditionItem(label: String, statusText: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (passed) "Cumple" else "No cumple",
                tint = if (passed) TerminalGreen else CriticalRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = DarkOnBackground
            )
        }

        Text(
            text = statusText,
            fontSize = 11.sp,
            color = if (passed) TerminalGreen else CriticalRed,
            fontWeight = FontWeight.SemiBold
        )
    }
}

