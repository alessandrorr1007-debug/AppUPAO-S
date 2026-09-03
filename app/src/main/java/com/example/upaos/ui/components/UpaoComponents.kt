package com.example.upaos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.abs

// ---------- Logo monograma UPAO (dibujado, evita el bug de painterResource) ----------
@Composable
fun UpaoLogo(modifier: Modifier = Modifier, size: Dp = 80.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(
                brush = Brush.linearGradient(colors = listOf(UpaoBlue, UpaoBlueDark))
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

// ---------- Gauge circular compacto ----------
@Composable
fun CircularGauge(
    progress: Float,
    centerValue: String,
    centerLabel: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    gaugeColor: Color = UpaoOrange,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "gaugeProgress"
    )
    val stroke = strokeWidth
    val cap = if (progress >= 1f) StrokeCap.Butt else StrokeCap.Round

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
                brush = Brush.sweepGradient(
                    colors = listOf(gaugeColor, gaugeColor.copy(alpha = 0.70f)),
                    center = Offset(this.size.width / 2f, this.size.height / 2f)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = cap)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (centerLabel.isNotEmpty()) {
                Text(
                    text = centerLabel,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- Botón principal con densidad optimizada (altura 44.dp) ----------
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (enabled && !loading) 2.dp else 0.dp,
            pressedElevation = 1.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier.height(height)
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "primaryBtn"
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- Campo de texto compacto ----------
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        placeholder = placeholder?.let { txt -> { Text(txt, fontSize = 13.sp) } },
        leadingIcon = leadingIcon?.let { ic -> { Icon(ic, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { txt -> { Text(txt, fontSize = 11.sp) } },
        modifier = modifier
    )
}

// ---------- Tarjeta base con densidad ajustada (-20% altura) ----------
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    corner: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    if (onClick != null) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = color),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = color),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

// ---------- Encabezado de sección ----------
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

// ---------- Badge de estado compacto ----------
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

// ---------- Estado vacío ----------
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------- Vista de error ----------
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    AppCard(
        color = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(12.dp),
        corner = 14.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Error de consulta",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry, modifier = Modifier.height(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reintentar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Diálogo reutilizable ----------
@Composable
fun ReusableDialog(
    title: String,
    text: String,
    confirmLabel: String = "Aceptar",
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    )
}

// ---------- Skeleton loading ----------
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SkeletonCourseCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp), corner = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(modifier = Modifier.size(36.dp), corner = 10.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(12.dp), corner = 6.dp)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp), corner = 5.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(32.dp), corner = 10.dp)
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

fun estadoNota(value: Any?): String = when {
    isPendiente(value) -> "Pendiente"
    (gradeValue(value) ?: 0.0) >= 10.5 -> "Aprobado"
    else -> "Desaprobado"
}

// ---------- Nombre de curso en Title Case ----------
private val palabrasCortas = setOf(
    "de", "del", "la", "las", "los", "el", "y", "e", "o", "u",
    "en", "a", "para", "por", "con", "al", "un", "una"
)

fun toTitleCase(texto: String?): String {
    val t = texto?.trim().orEmpty()
    if (t.isEmpty()) return t
    return t.lowercase().split(' ').joinToString(" ") { palabra ->
        if (palabra.isEmpty()) "" else if (palabra in palabrasCortas) palabra else palabra.replaceFirstChar { it.uppercase() }
    }
}

// ---------- Color identificador por curso ----------
private val cursoPalette = listOf(
    Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFF0D9488), Color(0xFFD97706),
    Color(0xFFEF4444), Color(0xFF16A34A), Color(0xFFEC4899), Color(0xFF6366F1),
    Color(0xFF14B8A6), Color(0xFFF97316)
)

fun cursoColor(nombre: String?): Color {
    val base = (nombre ?: "curso").lowercase()
    val hash = base.fold(0) { acc, c -> (acc * 31 + c.code) % 100000 }
    return cursoPalette[abs(hash) % cursoPalette.size]
}

// ---------- Contenedor reutilizable para Pull-to-Refresh ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) {
            onRefresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pullState.isRefreshing) {
            pullState.endRefresh()
        }
    }
    Box(modifier = modifier.nestedScroll(pullState.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(0f),
            state = pullState
        )
    }
}


