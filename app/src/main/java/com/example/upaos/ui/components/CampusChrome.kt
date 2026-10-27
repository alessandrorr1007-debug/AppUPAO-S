package com.example.upaos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Shared chrome; callers retain their titles, actions and navigation callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Column(modifier) {
        TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions,
            colors = colors, scrollBehavior = scrollBehavior)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = .22f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f),
            MaterialTheme.colorScheme.background
        ))))
    }
}

@Composable
fun CampusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
}
