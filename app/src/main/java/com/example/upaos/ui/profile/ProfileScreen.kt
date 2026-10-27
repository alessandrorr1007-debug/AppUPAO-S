package com.example.upaos.ui.profile
import com.example.upaos.ui.components.CampusTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.upaos.ui.components.AppCard

@Composable
fun RefreshProfileOnResume(model: ProfileViewModel) {
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CampusTopBar(title = { Text("Mi perfil", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val sections = state.profile?.sections.orEmpty().filterNot {
                it.title.startsWith("Dirección") || it.title == "Información adicional"
            }
            if (sections.isEmpty()) item {
                AppCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
                    Text(if (state.loading) "Consultando tu perfil…" else "Todavía no hay datos de perfil disponibles.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            sections.forEach { section -> item {
                AppCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    section.fields.forEachIndexed { index, field ->
                        Spacer(Modifier.height(12.dp))
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer { Text(field.value, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            } }
            item { OutlinedButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Actualizar perfil") } }
        }
    }
}
