package com.example.upaos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared agenda presentation; callers keep their own dates, data and detail actions. */
@Composable
fun ClassTimelineRow(
    start: String,
    end: String,
    title: String,
    room: String,
    accent: Color,
    connectAbove: Boolean,
    connectBelow: Boolean,
    detail: String? = null,
    detailColor: Color? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val badgeColor = detailColor ?: accent

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(48.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(start, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(end, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(16.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            val y = 16.dp.toPx()
            if (connectAbove) drawLine(track, Offset(x, 0f), Offset(x, y), 1.5.dp.toPx())
            if (connectBelow) drawLine(track, Offset(x, y), Offset(x, size.height), 1.5.dp.toPx())
            drawCircle(accent.copy(alpha = .18f), 6.5.dp.toPx(), Offset(x, y))
            drawCircle(accent, 3.5.dp.toPx(), Offset(x, y))
        })
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, bottom = 6.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = RoundedCornerShape(12.dp),
            color = if (highlighted) accent.copy(alpha = .10f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accent.copy(alpha = if (highlighted) 0.5f else 0.25f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .background(accent)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            toTitleCase(title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    room,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!detail.isNullOrBlank()) {
                                Surface(
                                    color = badgeColor.copy(alpha = .14f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = detail,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (onClick != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
