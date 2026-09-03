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
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.google.gson.Gson
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ProximoCursoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            GlanceTheme {
                ProximoCursoContent(data)
            }
        }
    }

    private suspend fun loadData(context: Context): ProximoCursoWidgetData {
        return try {
            val gson = Gson()
            val cache = ApiCache(context)
            val usuario = TokenManager(context).getSavedUser() ?: "anonimo"
            val json = cargarHorarioFavorito(cache, usuario)
                ?: return ProximoCursoWidgetData("Inicia sesión para ver tu próxima clase")
            val body = gson.fromJson(json, HorarioResponse::class.java)
            val proxima = calcularProximaClase(body.cursos)
            if (proxima == null) {
                ProximoCursoWidgetData("Sin clases próximas")
            } else {
                ProximoCursoWidgetData(
                    cursoNombre = proxima.curso.displayNombre,
                    hora = proxima.bloque.horaInicio12h ?: proxima.bloque.horaInicio,
                    aula = proxima.bloque.aula,
                    dia = etiquetaDia(proxima),
                    esAhora = proxima.esAhora
                )
            }
        } catch (e: Exception) {
            ProximoCursoWidgetData("Inicia sesión para ver tu próxima clase")
        }
    }

    private suspend fun cargarHorarioFavorito(cache: ApiCache, usuario: String): String? {
        val especifica = cache.cargar("horario_${usuario}_")
        if (especifica != null) return especifica
        val disponibles = cache.listarPorPrefijo("horario_")
            .filterKeys { it.endsWith(usuario) || it.contains(usuario) }
        if (disponibles.isNotEmpty()) {
            return disponibles.values.maxByOrNull { it.length }
        }
        val cualquiera = cache.listarPorPrefijo("horario_")
        return cualquiera.values.maxByOrNull { it.length }
    }

    private data class ProximaClaseInfo(
        val curso: HorarioCurso,
        val bloque: HorarioBloque,
        val diasRestantes: Int,
        val esAhora: Boolean,
        val inicio: Int
    )

    private fun diaHoy(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun minutosAhora(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun minutosDe(hhmm: String?): Int? {
        if (hhmm.isNullOrBlank()) return null
        val partes = hhmm.split(":")
        if (partes.size < 2) return null
        return (partes[0].toIntOrNull() ?: return null) * 60 + (partes[1].toIntOrNull() ?: return null)
    }

    private fun etiquetaDia(p: ProximaClaseInfo): String = when {
        p.esAhora -> "En curso ahora"
        p.diasRestantes == 0 -> "Hoy"
        else -> p.bloque.diaNombre ?: ""
    }

    private fun calcularProximaClase(cursos: List<HorarioCurso>): ProximaClaseInfo? {
        val hoy = diaHoy()
        val ahora = minutosAhora()
        var mejor: ProximaClaseInfo? = null
        for (curso in cursos) {
            for (bloque in curso.bloques) {
                val dia = bloque.dia ?: continue
                val inicio = minutosDe(bloque.horaInicio) ?: continue
                val fin = minutosDe(bloque.horaFin) ?: inicio
                val restantes = (dia - hoy + 7) % 7
                val esAhora = restantes == 0 && inicio <= ahora && ahora < fin
                if (esAhora) return ProximaClaseInfo(curso, bloque, restantes, true, inicio)
                if (restantes == 0 && inicio <= ahora) continue
                val candidato = ProximaClaseInfo(curso, bloque, restantes, false, inicio)
                val m = mejor
                if (m == null ||
                    restantes < m.diasRestantes ||
                    (restantes == m.diasRestantes && inicio < m.inicio)
                ) {
                    mejor = candidato
                }
            }
        }
        return mejor
    }

    fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val ids = manager.getGlanceIds(ProximoCursoWidget::class.java)
            ids.forEach { id -> ProximoCursoWidget.update(context, id) }
        }
    }
}

data class ProximoCursoWidgetData(
    val cursoNombre: String = "Inicia sesión",
    val hora: String? = null,
    val aula: String? = null,
    val dia: String? = null,
    val esAhora: Boolean = false
)

@Composable
private fun ProximoCursoContent(data: ProximoCursoWidgetData) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1A237E)))
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Text(
            text = if (data.esAhora) "CLASE EN CURSO" else "PRÓXIMA CLASE",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFFC107)),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (data.cursoNombre == "Inicia sesión" || data.cursoNombre == "Sin clases próximas") {
            Text(
                text = data.cursoNombre,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
        } else {
            Text(
                text = data.cursoNombre,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "HORA ${data.hora ?: "—"}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
                if (data.aula != null) {
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    Text(
                        text = "AULA ${data.aula}",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = data.dia ?: "",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF90CAF9)),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
        }
    }
}

class ProximoCursoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProximoCursoWidget
}
