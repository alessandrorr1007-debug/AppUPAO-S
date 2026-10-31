package com.example.upaos.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.ui.theme.campusSuccess

/** Same visual language for today's preview and the notification inbox. */
@Composable
fun NotificationCard(
    notice: NotificacionItem,
    onClick: () -> Unit,
    compact: Boolean = false,
    timeLabel: String = tiempoRelativo(notice.fechaCreacion)
) {
    val absence = notice.mensaje.contains("falta", ignoreCase = true)
    val attendance = absence || notice.componente?.contains("asistencia", ignoreCase = true) == true ||
        notice.mensaje.contains("asistencia", ignoreCase = true)
    val accent = when {
        absence -> MaterialTheme.colorScheme.error
        attendance -> campusSuccess
        else -> MaterialTheme.colorScheme.primary
    }
    val category = when {
        absence -> "Falta registrada"
        attendance -> "Asistencia"
        else -> "Calificación"
    }
    val icon = when {
        absence -> Icons.Filled.WarningAmber
        attendance -> Icons.AutoMirrored.Filled.FactCheck
        else -> Icons.AutoMirrored.Filled.MenuBook
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = if (notice.leida) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (notice.leida) MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f) else accent.copy(alpha = .35f))
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = accent.copy(alpha = .10f), shape = RoundedCornerShape(12.dp)) {
                    Icon(icon, null, Modifier.padding(10.dp).size(19.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent)
                    if (timeLabel.isNotBlank()) Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!notice.leida) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                        Text("Sin leer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = accent)
                    }
                } else if (compact) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                notice.curso?.takeIf { it.isNotBlank() }?.let {
                    Text(toTitleCase(it), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
                if (!attendance) notice.componente?.takeIf { it.isNotBlank() }?.let {
                    Text(toTitleCase(it), style = MaterialTheme.typography.labelMedium, color = accent)
                }
                Text(limpiarMensajeNotificacion(notice.mensaje, notice.curso),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                if (!notice.leida) {
                    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar como leída", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Leída", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
