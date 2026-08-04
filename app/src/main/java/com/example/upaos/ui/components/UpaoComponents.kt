package com.example.upaos.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.ui.theme.UpaoBlue
import com.example.upaos.ui.theme.UpaoBlueDark
import com.example.upaos.ui.theme.UpaoGray
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoOrange
import com.example.upaos.ui.theme.UpaoOrangeBright
import com.example.upaos.ui.theme.UpaoRed

// ---------- Logo monograma UPAO (dibujado, evita el bug de painterResource) ----------
@Composable
fun UpaoLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.linearGradient(listOf(UpaoBlue, UpaoBlueDark)),
                shape = RoundedCornerShape(size / 4)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                    append("U")
                }
                withStyle(SpanStyle(color = UpaoOrangeBright, fontWeight = FontWeight.Black)) {
                    append("PAO")
                }
            },
            fontSize = (size.value * 0.30f).sp,
            letterSpacing = 1.sp
        )
    }
}

// ---------- Gauge circular (promedio general) ----------
@Composable
fun CircularGauge(
    progress: Float,
    centerValue: String,
    centerLabel: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    gaugeColor: Color = UpaoOrange
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "gaugeProgress"
    )
    val stroke = 9.dp
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = gaugeColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (centerLabel.isNotEmpty()) {
                Text(
                    text = centerLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- Utilidades de color por estado de nota ----------
fun isPendiente(value: Any?): Boolean =
    value == null || value.toString().isBlank() || value.toString() == "null"

fun gradeValue(value: Any?): Double? {
    if (value == null) return null
    return value.toString().trim().toDoubleOrNull()
}

fun gradeColor(value: Any?): Color {
    if (isPendiente(value)) return UpaoGray
    val v = gradeValue(value)
    return when {
        v != null && v >= 10.5 -> UpaoGreen
        v != null -> UpaoRed
        else -> UpaoGray
    }
}
