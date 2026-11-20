package com.medibot.g1.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.HospitalNode
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.ui.theme.*

@Composable
fun HospitalMapCanvas(
    currentNode: HospitalNode,
    progressPct: Int,
    operationalState: OperationalState,
    modifier: Modifier = Modifier,
    missionName: String? = null,
    isStale: Boolean = false
) {
    val nodes = listOf(
        HospitalNode.DOCK_BASE,
        HospitalNode.LAB_CENTRAL,
        HospitalNode.FIRE_DOOR_PB,
        HospitalNode.ELEVATOR_BANK,
        HospitalNode.SAFE_POINT_P2,
        HospitalNode.UTI_NURSING
    )

    val currentIndex = nodes.indexOf(currentNode).coerceAtLeast(0)

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, if (isStale) CardStaleBorder else CardNormalBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .alpha(if (isStale) 0.55f else 1f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MAPA TÁCTICO 2D (PB ↔ PISO 2)",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Current floor pill
                val floorText = when (currentNode.floor) {
                    0 -> "PLANTA BAJA"
                    2 -> "PISO 2"
                    else -> "ASCENSOR"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = floorText,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas drawing the circuit nodes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val spacing = width / (nodes.size)
                    val centerY = height / 2

                    // Draw connecting track
                    drawLine(
                        color = CardNormalBorder,
                        start = Offset(spacing * 0.5f, centerY),
                        end = Offset(width - (spacing * 0.5f), centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Draw completed track
                    val completedX = (spacing * 0.5f) + (currentIndex.toFloat() / (nodes.size - 1)) * (width - spacing)
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.8f),
                        start = Offset(spacing * 0.5f, centerY),
                        end = Offset(completedX, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Place nodes horizontally
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    nodes.forEachIndexed { index, node ->
                        val isCurrent = node == currentNode
                        val isPassed = index < currentIndex

                        val nodeColor = when {
                            isCurrent && operationalState == OperationalState.INCIDENT_BLOCKED -> CriticalRed
                            isCurrent && operationalState == OperationalState.EMERGENCY_STOPPED -> CriticalRed
                            isCurrent && operationalState == OperationalState.LOW_BATTERY_CROUCHED -> WarningAmber
                            isCurrent -> NeonCyan
                            isPassed -> TerminalGreen
                            else -> StaleGray
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (isCurrent) 28.dp else 20.dp)
                                    .background(
                                        if (isCurrent) nodeColor.copy(alpha = pulseAlpha * 0.4f) else Color.Transparent,
                                        CircleShape
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 18.dp else 12.dp)
                                        .clip(CircleShape)
                                        .background(nodeColor)
                                        .border(2.dp, DarkBackground, CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = when (node) {
                                    HospitalNode.DOCK_BASE -> "BASE"
                                    HospitalNode.LAB_CENTRAL -> "LAB"
                                    HospitalNode.FIRE_DOOR_PB -> "CORTAF."
                                    HospitalNode.ELEVATOR_BANK -> "ASCENS."
                                    HospitalNode.SAFE_POINT_P2 -> "SEGURO"
                                    HospitalNode.UTI_NURSING -> "UTI"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) nodeColor else DarkOnSurface.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current Node Banner & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "POSICIÓN ACTUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurface,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = currentNode.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isStale) "PROGRESO (DATO ANTIGUO)" else "PROGRESO MISIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurface,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$progressPct%",
                        style = MaterialTheme.typography.titleMedium,
                        color = TerminalGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = missionName?.let { "Misión: $it" } ?: "Sin misión activa",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressPct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TerminalGreen,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

