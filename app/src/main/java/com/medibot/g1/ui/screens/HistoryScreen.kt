package com.medibot.g1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.EventRecord
import com.medibot.g1.data.model.MissionRecord
import com.medibot.g1.data.model.MissionStatus
import androidx.compose.runtime.saveable.rememberSaveable
import com.medibot.g1.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    missionsHistory: List<MissionRecord>,
    eventLogs: List<EventRecord>,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("MISIONES EJECUTADAS", "AUDITORÍA / EVENTOS")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header & Actions
        Column(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "HISTORIAL Y AUDITORÍA (ROOM)",
                    style = MaterialTheme.typography.titleLarge,
                    color = DarkOnBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Solo lectura · persistencia local (Room)",
                    fontSize = 11.sp,
                    color = DarkOnSurface
                )
            }

        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = NeonCyan,
            divider = { HorizontalDivider(color = CardNormalBorder) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (selectedTab == index) NeonCyan else DarkOnSurface
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        if (selectedTab == 0) {
            MissionsTabContent(missions = missionsHistory)
        } else {
            EventsTabContent(events = eventLogs)
        }
    }

}

@Composable
private fun MissionsTabContent(missions: List<MissionRecord>) {
    if (missions.isEmpty()) {
        EmptyState(message = "No hay misiones persistidas en Room.\nEjecute un escenario o cargue datos de ejemplo desde el panel de ingeniería.")
    } else {
        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(missions, key = { it.runId }) { item ->
                val statusColor = when (item.status) {
                    MissionStatus.COMPLETED -> TerminalGreen
                    MissionStatus.CANCELLED -> WarningAmber
                    MissionStatus.IN_PROGRESS -> NeonCyan
                    MissionStatus.FAILED -> CriticalRed
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, CardNormalBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.missionName,
                                style = MaterialTheme.typography.titleMedium,
                                color = DarkOnBackground,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.15f))
                                    .border(1.dp, statusColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.status.label.uppercase(),
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${item.originName}  ➔  ${item.destinationName}",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.description,
                            fontSize = 11.sp,
                            color = DarkOnSurface
                        )

                        if (item.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.notes,
                                fontSize = 11.sp,
                                color = DarkOnBackground
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val durationSec = ((item.endTime - item.startTime) / 1000).coerceAtLeast(0)
                            Text(
                                text = "${item.runId} · Fin ${dateFormat.format(Date(item.endTime))} (${durationSec}s)",
                                fontSize = 10.sp,
                                color = DarkOnSurface
                            )
                            Text(
                                text = "Bat. ${item.finalBatteryPct}% · Tmáx ${item.maxTempCelsius}°C · Vmáx ${item.maxVibrationG}G",
                                fontSize = 10.sp,
                                color = DarkOnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsTabContent(events: List<EventRecord>) {
    if (events.isEmpty()) {
        EmptyState(message = "Log de auditoría vacío en Room.\nLas acciones del operador y eventos se registrarán aquí automáticamente.")
    } else {
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(events, key = { it.eventId }) { event ->
                val severityColor = when (event.severity) {
                    AlertSeverity.CRITICAL -> CriticalRed
                    AlertSeverity.WARNING -> WarningAmber
                    AlertSeverity.INFO -> NeonCyan
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, CardNormalBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(severityColor)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = event.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )

                                Text(
                                    text = timeFormat.format(Date(event.timestamp)),
                                    fontSize = 10.sp,
                                    color = DarkOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = event.details,
                                fontSize = 11.sp,
                                color = DarkOnSurface,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkBackground)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = listOfNotNull(event.category.name, event.severity.name, event.operatorId).joinToString(" • "),
                                    fontSize = 9.sp,
                                    color = severityColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = StaleGray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

