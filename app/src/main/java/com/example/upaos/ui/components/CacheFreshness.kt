package com.example.upaos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.GradesCache
import com.example.upaos.ui.home.Freshness

@Composable
fun CacheFreshness(key: String, saved: Boolean, loading: Boolean, grades: Boolean = false) {
    val context = LocalContext.current
    val flow = remember(key, grades) { if (grades) GradesCache(context).observarFecha(key) else ApiCache(context).observarFecha(key) }
    val updated by flow.collectAsState(initial = null)
    Column(Modifier.padding(vertical = 6.dp)) { Freshness(updated, !saved && updated != null, loading) }
}
