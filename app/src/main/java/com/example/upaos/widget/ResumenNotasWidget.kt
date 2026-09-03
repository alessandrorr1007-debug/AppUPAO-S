package com.example.upaos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.upaos.MainActivity
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.model.GradesResponse
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ResumenNotasWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            GlanceTheme {
                ResumenNotasContent(data)
            }
        }
    }

    private suspend fun loadData(context: Context): NotasWidgetData {
        return try {
            val gson = Gson()
            val cache = GradesCache(context)
            val usuario = com.example.upaos.data.local.TokenManager(context).getSavedUser()
            val clave = "notas_${usuario ?: "anonimo"}"
            val json = cache.cargar(clave) ?: return NotasWidgetData()
            val body = gson.fromJson(json, GradesResponse::class.java)
            NotasWidgetData(
                promedio = body.promedioGeneral?.toString()?.toDoubleOrNull(),
                totalCursos = body.cursos.size,
                ultimaActualizacion = body.ultimaActualizacion
            )
        } catch (e: Exception) {
            NotasWidgetData()
        }
    }

    fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val ids = manager.getGlanceIds(ResumenNotasWidget::class.java)
            ids.forEach { id -> ResumenNotasWidget.update(context, id) }
        }
    }
}

data class NotasWidgetData(
    val promedio: Double? = null,
    val totalCursos: Int = 0,
    val ultimaActualizacion: String? = null
)

@Composable
private fun ResumenNotasContent(data: NotasWidgetData) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFFFFFF)))
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "UPAO · Mis Notas",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF3B82F6)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.promedio?.let {
                        if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                    } ?: "—",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF111111)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column {
                    Text(
                        text = "Promedio",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF666666)),
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "${data.totalCursos} cursos",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF666666)),
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            Text(
                text = "Toca para ver tus notas",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF3B82F6)),
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
            )
        }
    }
}

class ResumenNotasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResumenNotasWidget
}
