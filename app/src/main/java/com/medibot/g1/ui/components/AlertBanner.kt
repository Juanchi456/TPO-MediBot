package com.medibot.g1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.RobotAlert
import com.medibot.g1.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertBanner(
    alerts: List<RobotAlert>,
    canAcknowledge: Boolean,
    onAcknowledgeAlert: (String) -> Unit,
    modifier: Modifier = Modifier,
    isStale: Boolean = false
) {
    if (alerts.isEmpty()) return

    // Primero lo que exige acción: sin reconocer, por severidad y luego las más recientes.
    val ordered = alerts.sortedWith(
        compareBy<RobotAlert> { it.isAcknowledged }
            .thenByDescending { it.severity.ordinal }
            .thenByDescending { it.timestamp }
    )
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isStale) 0.55f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ordered.forEach { alert ->
            val color = when (alert.severity) {
                AlertSeverity.CRITICAL -> CriticalRed
                AlertSeverity.WARNING -> WarningAmber
                AlertSeverity.INFO -> NeonCyan
            }

            val icon = when (alert.severity) {
                AlertSeverity.CRITICAL -> Icons.Default.Error
                AlertSeverity.WARNING -> Icons.Default.Warning
                AlertSeverity.INFO -> Icons.Default.Info
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f))
                    .border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Severidad ${alert.severity.name}",
                        tint = color,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            if (alert.isAcknowledged) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TerminalGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = alert.acknowledgedAt
                                            ?.let { "ACK ${timeFormat.format(Date(it))}" } ?: "ACK",
                                        color = TerminalGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnBackground,
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "Emitida ${timeFormat.format(Date(alert.timestamp))}",
                            fontSize = 11.sp,
                            color = DarkOnSurface
                        )

                        if (!alert.isAcknowledged) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onAcknowledgeAlert(alert.id) },
                                enabled = canAcknowledge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = color,
                                    contentColor = DarkBackground,
                                    disabledContainerColor = DarkSurfaceVariant,
                                    disabledContentColor = StaleGray
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "RECONOCER (ACK)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

