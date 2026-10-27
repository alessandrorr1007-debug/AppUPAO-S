package com.example.upaos.service

import android.content.Context
import com.example.upaos.data.refresh.*
import android.util.Log
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.upao.UpaoRepository
import com.example.upaos.widget.ResumenNotasWidget
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

internal class GradeSyncCheck(private val applicationContext: Context) {
    private val tokenManager = TokenManager(applicationContext)
    private val user = tokenManager.getSavedUser() ?: throw CancellationException("Sin cuenta")
    private val owner = ReminderOwner(user, tokenManager.getSessionId() ?: throw CancellationException("Sin sesión"))
    private val notificationPrefs = NotificationPreferences(applicationContext, user)
    private val gradesCache = GradesCache(applicationContext)
    private val upaoRepository = UpaoRepository(applicationContext)
    private val gson = Gson()
    private var changes = 0
    private fun requireCurrentSession() {
        if (!owner.matches(tokenManager.getSavedUser(), tokenManager.getSessionId())) throw CancellationException("Sesión cambiada")
    }
    private fun publishForCurrentSession(publish: () -> Unit) {
        if (!com.example.upaos.data.local.SessionCleanup.publishIfCurrent(owner,
                { tokenManager.getSavedUser() to tokenManager.getSessionId() }, publish)) throw CancellationException("Sesión cambiada")
    }
    suspend fun check(mode: String) {
        val started = android.os.SystemClock.elapsedRealtime()
        changes = 0
        var outcome = "error"
        try {
            requireCurrentSession()
            if (notificationPrefs.syncAcademicPeriod == null) upaoRepository.getPeriodos()
            revisarNotas(user).requireComplete()
            outcome = "ok"
        } finally {
            Log.i(TAG, "timestamp=${java.time.Instant.now()} modo=$mode duracionMs=${android.os.SystemClock.elapsedRealtime()-started} cambios=$changes resultado=$outcome")
        }
    }
    private suspend fun revisarNotas(user: String): RefreshResult<Unit> = captureRefresh("Notas") {
        val cachedJson = gradesCache.cargar("notas_$user")
        val cached = if (cachedJson.isNullOrBlank()) null else try {
            gson.fromJson(cachedJson, GradesResponse::class.java)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w(TAG, "Metadatos de notas en caché inválidos; se consultará el periodo"); null }
        val periodo = notificationPrefs.syncAcademicPeriod
            ?: throw com.example.upaos.data.upao.UpaoUnexpectedFormatException("No hay periodo activo")
        val carrera = cached?.takeIf { it.periodo == periodo }?.carrera?.takeIf { it.isNotBlank() }
            ?: upaoRepository.getCarreras(periodo).carreras.firstOrNull() ?: "UG"
        publishComplete(upaoRepository.buscarNotasResult(periodo, carrera)) { grades ->
            currentCoroutineContext().ensureActive()
            requireCurrentSession()
            compararYNotificarNotas(grades.cursos, user)
            gradesCache.guardar("notas_$user", gson.toJson(grades))
            notificationPrefs.ultimaRevisionNotas = System.currentTimeMillis()
        }
        try { ResumenNotasWidget.updateAll(applicationContext) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w(TAG, "No se pudo actualizar el widget de notas") }
        Unit
    }

    private fun emitGrade(title: String, body: String, identity: String) {
        val prefs = applicationContext.getSharedPreferences("upaos_notification_snapshots_$user", Context.MODE_PRIVATE)
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest("${notificationPrefs.syncAcademicPeriod}/$identity".toByteArray()).joinToString("") { "%02x".format(it) }
        if (prefs.contains("grade_notified_$fingerprint")) return
        NotificationService.mostrarNotificacionNotas(applicationContext, title, body)
        kotlin.check(prefs.edit().putBoolean("grade_notified_$fingerprint", true).commit()) { "No se pudo persistir dedupe" }
        changes++
    }

    private fun limpiarNota(v: Any?): String? {
        if (v == null) return null
        val s = v.toString().trim()
        if (s.isEmpty() || s == "null" || s == "None" || s == "-" || s == "s/n" || s == "SN") return null
        val d = s.toDoubleOrNull()
        return if (d != null) {
            if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(java.util.Locale.US, d)
        } else {
            s
        }
    }

    private fun simplificarNombreComponente(nombre: String?): String {
        val t = nombre?.trim().orEmpty().uppercase(java.util.Locale.ROOT)
        return when {
            t.contains("EP1") || t.contains("PROCESO 1") || t.contains("PERMANENTE 1") || t.contains("EP 1") -> "EP1"
            t.contains("EP2") || t.contains("PROCESO 2") || t.contains("PERMANENTE 2") || t.contains("EP 2") -> "EP2"
            t.contains("PARCIAL") || t.contains("EVP") -> "Parcial"
            t.contains("FINAL") || t.contains("EVF") -> "Final"
            else -> com.example.upaos.ui.components.toTitleCase(nombre)
        }
    }

    private suspend fun compararYNotificarNotas(nuevosCursos: List<CourseGrade>, user: String) {
        val snapshotPrevioRaw = notificationPrefs.ultimoSnapshotNotas
        val type = object : TypeToken<Map<String, SnapshotNotasCurso>>() {}.type
        val snapshotPrevio: Map<String, SnapshotNotasCurso> = if (!snapshotPrevioRaw.isNullOrBlank()) {
            try {
                gson.fromJson(snapshotPrevioRaw, type) ?: emptyMap()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val nuevoSnapshot = mutableMapOf<String, SnapshotNotasCurso>()

        for (curso in nuevosCursos) {
            val key = curso.crn?.takeIf { it.isNotBlank() } ?: curso.displayNombre
            val cursoNombre = com.example.upaos.ui.components.toTitleCase(curso.displayNombre)
            val notaActualStr = limpiarNota(curso.displayNotaActual)
            val ep1NotaStr = limpiarNota(curso.displayEp1.nota)
            val ep2NotaStr = limpiarNota(curso.displayEp2.nota)

            val compMap = mutableMapOf<String, String?>()
            val subMap = mutableMapOf<String, String?>()

            // 1. Mapear componentes y subcomponentes directamente desde curso.componentes
            for (comp in curso.componentes) {
                val compKey = comp.componentId?.toString() ?: comp.codigo ?: comp.displayNombre
                val compNota = limpiarNota(comp.displayNota)
                compMap[compKey] = compNota

                for (sub in comp.subcomponentes) {
                    val subKey = "${compKey}_${sub.codigo ?: sub.displayNombre}"
                    val subNota = limpiarNota(sub.displayNota)
                    subMap[subKey] = subNota
                }
            }

            // Compatibilidad de respaldo si curso.componentes viniera vacío pero displayEp1/Ep2 tuviera notas
            if (compMap.isEmpty()) {
                if (!ep1NotaStr.isNullOrBlank()) compMap["EP1"] = ep1NotaStr
                if (!ep2NotaStr.isNullOrBlank()) compMap["EP2"] = ep2NotaStr
            }

            nuevoSnapshot[key] = SnapshotNotasCurso(
                nombre = curso.displayNombre,
                notaActual = notaActualStr,
                ep1Nota = ep1NotaStr,
                ep2Nota = ep2NotaStr,
                componentes = compMap,
                subcomponentes = subMap
            )

            // La primera lectura establece la línea base sin avisar notas históricas.
            if (snapshotPrevio.isNotEmpty()) {
                val previo = snapshotPrevio[key] ?: SnapshotNotasCurso(nombre = curso.displayNombre, notaActual = null)
                run {
                    var algunSubNotificado = false

                    // Caso 1: Revisar SUBCOMPONENTES (subprocesos como prácticas, trabajos, talleres, etc.)
                    for (comp in curso.componentes) {
                        val compKey = comp.componentId?.toString() ?: comp.codigo ?: comp.displayNombre
                        val compNombreCorto = simplificarNombreComponente(comp.displayNombre)

                        for (sub in comp.subcomponentes) {
                            val subKey = "${compKey}_${sub.codigo ?: sub.displayNombre}"
                            val notaActualSub = subMap[subKey]
                            val notaPreviaSub = previo.subcomponentesMap[subKey]

                            if (!notaActualSub.isNullOrBlank() && notaActualSub != notaPreviaSub) {
                                algunSubNotificado = true
                                val subNombre = com.example.upaos.ui.components.toTitleCase(sub.displayNombre)
                                val etiqueta = if (compNombreCorto.isNotBlank() && !subNombre.contains(compNombreCorto, ignoreCase = true)) {
                                    "$compNombreCorto: $subNombre"
                                } else {
                                    subNombre
                                }

                                val titulo = if (notaPreviaSub.isNullOrBlank()) {
                                    "🎓 Subproceso calificado"
                                } else {
                                    "🎓 Subproceso actualizado"
                                }

                                val detalleSub = if (compNombreCorto.isNotBlank() && !subNombre.contains(compNombreCorto, ignoreCase = true)) {
                                    "$subNombre ($compNombreCorto)"
                                } else {
                                    subNombre
                                }

                                val mensaje = if (notaPreviaSub.isNullOrBlank()) {
                                    "$cursoNombre: Calificación en $detalleSub: $notaActualSub."
                                } else {
                                    "$cursoNombre: Tu nota en $detalleSub cambió de $notaPreviaSub a $notaActualSub."
                                }

                                publishForCurrentSession { emitGrade(titulo, mensaje, "$key/sub/$subKey/$notaActualSub") }
                                upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = etiqueta)
                            }
                        }
                    }

                    // Caso 2: Revisar componentes principales (Examen Parcial, Examen Final, o EP1/EP2)
                    var algunCompNotificado = false
                    for (comp in curso.componentes) {
                        val compKey = comp.componentId?.toString() ?: comp.codigo ?: comp.displayNombre
                        val notaActualComp = compMap[compKey]
                        val notaPreviaComp = previo.componentesMap[compKey]

                        if (!notaActualComp.isNullOrBlank() && notaActualComp != notaPreviaComp) {
                            run {
                                algunCompNotificado = true
                                val compNombre = com.example.upaos.ui.components.toTitleCase(comp.displayNombre)
                                val titulo = if (notaPreviaComp.isNullOrBlank()) {
                                    "🎓 Nueva nota en $compNombre"
                                } else {
                                    "🎓 Nota actualizada en $compNombre"
                                }
                                val mensaje = if (notaPreviaComp.isNullOrBlank()) {
                                    "$cursoNombre: Calificación en $compNombre: $notaActualComp."
                                } else {
                                    "$cursoNombre: Tu nota en $compNombre cambió de $notaPreviaComp a $notaActualComp."
                                }
                                publishForCurrentSession { emitGrade(titulo, mensaje, "$key/comp/$compKey/$notaActualComp") }
                                upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = compNombre)
                            }
                        }
                    }

                    // Caso 3: Cambio en el promedio actual del curso (si no se notificó ningún sub o comp individual)
                    if (!notaActualStr.isNullOrBlank() && notaActualStr != previo.notaActual && !algunSubNotificado && !algunCompNotificado) {
                        val titulo = "🎓 Nota promedio actualizada"
                        val mensaje = "$cursoNombre: Tu promedio actual ahora es $notaActualStr."
                        publishForCurrentSession { emitGrade(titulo, mensaje, "$key/average/$notaActualStr") }
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = "Promedio Actual")
                    }
                }
            }
        }

        notificationPrefs.ultimoSnapshotNotas = gson.toJson(nuevoSnapshot)
    }

    private data class SnapshotNotasCurso(
        val nombre: String,
        val notaActual: String?,
        val ep1Nota: String? = null,
        val ep2Nota: String? = null,
        val componentes: Map<String, String?>? = emptyMap(),
        val subcomponentes: Map<String, String?>? = emptyMap()
    ) {
        val componentesMap: Map<String, String?>
            get() = componentes ?: emptyMap()
        val subcomponentesMap: Map<String, String?>
            get() = subcomponentes ?: emptyMap()
    }

    companion object { private const val TAG = "UPAO_GradeSync" }
}
