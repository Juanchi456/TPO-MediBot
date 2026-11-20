package com.medibot.g1.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibot.g1.data.model.TelemetryCardState
import com.medibot.g1.ui.theme.*

@Composable
fun TelemetryCard(
    title: String,
    value: String,
    unit: String = "",
    subtitle: String? = null,
    icon: ImageVector? = null,
    cardState: TelemetryCardState = TelemetryCardState.NORMAL,
    isStale: Boolean = false,
    staleSecondsAgo: Long = 0L,
    modifier: Modifier = Modifier
) {
    val effectiveState = if (isStale) TelemetryCardState.STALE else cardState

    val borderColor by animateColorAsState(
        targetValue = when (effectiveState) {
            TelemetryCardState.NORMAL -> CardNormalBorder
            TelemetryCardState.WARNING -> WarningAmber
            TelemetryCardState.ERROR -> CriticalRed
            TelemetryCardState.STALE -> CardStaleBorder
        },
        label = "cardBorderColor"
    )

    val contentAlpha = if (effectiveState == TelemetryCardState.STALE) 0.55f else 1.0f

    val accentColor = when (effectiveState) {
        TelemetryCardState.NORMAL -> NeonCyan
        TelemetryCardState.WARNING -> WarningAmber
        TelemetryCardState.ERROR -> CriticalRed
        TelemetryCardState.STALE -> StaleGray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .alpha(contentAlpha)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row: Title & Stale badge or Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurface,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )

                if (effectiveState == TelemetryCardState.STALE) {
                    Box(
                        modifier = Modifier
                            .background(WarningAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, WarningAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "hace ${staleSecondsAgo}s",
                            maxLines = 1,
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Value + Unit
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            // Subtitle
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

