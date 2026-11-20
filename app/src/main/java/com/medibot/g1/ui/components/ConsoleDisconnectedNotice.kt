package com.medibot.g1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medibot.g1.ui.theme.*

/** Se muestra en las vistas de operación cuando la consola no está conectada a ningún robot. */
@Composable
fun ConsoleDisconnectedNotice(
    onGoToRobotSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, CardStaleBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                tint = StaleGray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "CONSOLA DESCONECTADA",
                style = MaterialTheme.typography.titleLarge,
                color = DarkOnBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay un robot seleccionado. Sin conexión no se muestran datos en vivo ni se pueden emitir órdenes.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoToRobotSelection,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("SELECCIONAR ROBOT", fontWeight = FontWeight.Bold)
            }
        }
    }
}
