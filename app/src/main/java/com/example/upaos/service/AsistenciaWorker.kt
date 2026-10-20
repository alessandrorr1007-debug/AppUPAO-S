package com.example.upaos.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.upao.Calculators
import com.example.upaos.data.upao.UpaoAuth
import com.example.upaos.data.upao.UpaoRepository
import com.example.upaos.widget.ResumenNotasWidget
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AsistenciaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var notificationPrefs = NotificationPreferences(context)
    private val tokenManager = TokenManager(context)
    private val apiCache = ApiCache(context)
    private val gradesCache = GradesCache(context)
    private val upaoRepository = UpaoRepository(context)
    private val gson = Gson()
    private var owner: ReminderOwner? = null

    private fun requireCurrentSession() {
        if (owner?.matches(tokenManager.getSavedUser(), tokenManager.getSessionId()) != true)
            throw CancellationException("La sesión del trabajo cambió")
    }

    private fun publishForCurrentSession(publish: () -> Unit) {
        val expected = owner ?: throw CancellationException("No hay sesión del trabajo")
        if (!com.example.upaos.data.local.SessionCleanup.publishIfCurrent(expected,
                { tokenManager.getSavedUser() to tokenManager.getSessionId() }, publish))
            throw CancellationException("La sesión del trabajo cambió")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (com.example.upaos.data.refresh.ForegroundRefreshGate.active) return@withContext Result.success()
        if (!syncMutex.tryLock()) return@withContext Result.success()
        try {
            if (!tokenManager.shouldKeepLoggedIn()) return@withContext Result.success()
            val checkAsistencia = notificationPrefs.checkAsistenciaEnabled
            val checkNotas = notificationPrefs.checkNotasEnabled

            if (!checkAsistencia && !checkNotas) {
                Log.d(TAG, "Notificaciones de asistencia y notas desactivadas. Omitiendo revisión.")
                return@withContext Result.success()
            }

            val user = tokenManager.getSavedUser()
            val pass = tokenManager.getSavedPass()

            if (user.isNullOrBlank() || pass.isNullOrBlank()) {
                Log.d(TAG, "Sin credenciales guardadas, omitiendo chequeo.")
                return@withContext Result.success()
            }
            val sessionId = tokenManager.getSessionId() ?: return@withContext Result.success()
            owner = ReminderOwner(user, sessionId)
            requireCurrentSession()
            notificationPrefs = NotificationPreferences(applicationContext, user)

            try {
                // Asegurar sesión directa en UPAO SSO
                val loginRes = UpaoAuth.ensureSession(user, pass)
                if (!loginRes.success) {
                    Log.w(TAG, "No se pudo asegurar sesión directa con UPAO SSO.")
                    val fallos = notificationPrefs.syncConsecutiveFailures + 1
                    notificationPrefs.syncConsecutiveFailures = fallos
                    SyncScheduler.scheduleNext(applicationContext, fallosConsecutivos = fallos)
                    return@withContext Result.success()
                }

                // 1. Revisión de Asistencia
                if (checkAsistencia) {
                    revisarAsistencia(user)
                }

                // 2. Revisión de Notas
                if (checkNotas) {
                    revisarNotas(user)
                }

                // Reprograma la siguiente verificación periódica con éxito (resetea fallos a 0)
                publishForCurrentSession {
                    notificationPrefs.syncConsecutiveFailures = 0
                    SyncScheduler.scheduleNext(applicationContext, fallosConsecutivos = 0)
                }
                Log.d(TAG, "Sincronización en segundo plano completada con éxito. Próximo ciclo adaptativo programado.")
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                requireCurrentSession()
                Log.e(TAG, "No se pudo completar la sincronización")
                publishForCurrentSession {
                    val fallos = notificationPrefs.syncConsecutiveFailures + 1
                    notificationPrefs.syncConsecutiveFailures = fallos
                    SyncScheduler.scheduleNext(applicationContext, fallosConsecutivos = fallos)
                }
                Result.retry()
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun revisarAsistencia(user: String) {
        try {
            val res = upaoRepository.getAsistencia()
            requireCurrentSession()

            if (res.success && res.asistencia.isNotEmpty()) {
                val nuevaAsistencia = res.asistencia
                compararYNotificarAsistencia(nuevaAsistencia, user)

                val cacheKey = "asistencia_$user"
                apiCache.guardar(cacheKey, gson.toJson(res))
                notificationPrefs.ultimaRevisionAsistencia = System.currentTimeMillis()
                Log.d(TAG, "Revisión de asistencia completada.")
            } else {
                Log.w(TAG, "Respuesta vacía o no exitosa al consultar asistencia directa")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "No se pudo revisar la asistencia")
        }
    }

    private suspend fun revisarNotas(user: String) {
        try {
            var periodo = "202610"
            var carrera = "UG"

            val cachedJson = gradesCache.cargar("notas_$user")
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedGrades = gson.fromJson(cachedJson, GradesResponse::class.java)
                    if (!cachedGrades?.periodo.isNullOrBlank()) periodo = cachedGrades.periodo!!
                    if (!cachedGrades?.carrera.isNullOrBlank()) carrera = cachedGrades.carrera!!
                } catch (_: Exception) {}
            } else {
                try {
                    val periodos = upaoRepository.getPeriodos()
                    periodo = Calculators.detectarPeriodoActual(periodos.periodos, periodos.periodoActual)
                    val carreras = upaoRepository.getCarreras(periodo)
                    carrera = carreras.carreras.firstOrNull() ?: "UG"
                } catch (_: Exception) {}
            }

            val grades = upaoRepository.buscarNotas(periodo, carrera)
            requireCurrentSession()

            if (grades.cursos.isNotEmpty()) {
                compararYNotificarNotas(grades.cursos, user)

                gradesCache.guardar("notas_$user", gson.toJson(grades))
                notificationPrefs.ultimaRevisionNotas = System.currentTimeMillis()

                try {
                    ResumenNotasWidget.updateAll(applicationContext)
                } catch (_: Exception) {}

                Log.d(TAG, "Revisión de notas completada")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "No se pudieron revisar las notas")
        }
    }

    private suspend fun compararYNotificarAsistencia(nuevosCursos: List<AsistenciaCurso>, user: String) {
        val snapshotPrevioRaw = notificationPrefs.ultimoSnapshotAsistencia
        val type = object : TypeToken<Map<String, SnapshotCurso>>() {}.type
        val snapshotPrevio: Map<String, SnapshotCurso> = if (!snapshotPrevioRaw.isNullOrBlank()) {
            try {
                gson.fromJson(snapshotPrevioRaw, type) ?: emptyMap()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val nuevoSnapshot = mutableMapOf<String, SnapshotCurso>()

        for (curso in nuevosCursos) {
            val key = curso.crn?.takeIf { it.isNotBlank() }
                ?: "${curso.displayNombre}_${curso.seccion ?: ""}_${curso.periodo ?: ""}"

            val faltasActuales = curso.faltas ?: 0
            val pctActual = curso.porcentaje ?: 0.0
            val asistenciasActuales = curso.vecesAsistidas

            val tipoTag = when {
                !curso.tipo.isNullOrBlank() -> " (${curso.tipo})"
                !curso.tipoComponente.isNullOrBlank() -> " (${curso.tipoComponente})"
                !curso.seccion.isNullOrBlank() -> " (Sec. ${curso.seccion})"
                else -> ""
            }
            val nombreConTipo = "${curso.displayNombre}$tipoTag"

            nuevoSnapshot[key] = SnapshotCurso(
                nombre = nombreConTipo,
                faltas = curso.faltas,
                asistencias = asistenciasActuales,
                porcentaje = curso.porcentaje
            )

            // Solo comparamos si ya existía un snapshot previo
            if (snapshotPrevio.isNotEmpty()) {
                val previo = snapshotPrevio[key]
                if (previo != null) {
                    val faltasPrevias = previo.faltas ?: 0
                    val asistPrevias = previo.asistencias

                    if (faltasActuales > faltasPrevias) {
                        val dif = faltasActuales - faltasPrevias
                        val plural = if (dif == 1) "una nueva falta" else "$dif faltas nuevas"
                        val titulo = "⚠️ Falta registrada"
                        val mensaje = "$nombreConTipo: Te registraron $plural (Total: $faltasActuales). Asistencia: ${formatearPct(pctActual)}%."
                        publishForCurrentSession { NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje) }
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    } else if (asistenciasActuales != null && asistPrevias != null && asistenciasActuales > asistPrevias) {
                        val dif = asistenciasActuales - asistPrevias
                        val plural = if (dif == 1) "asistencia" else "$dif asistencias"
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró tu $plural! Llevas $asistenciasActuales asistencias."
                        publishForCurrentSession { NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje) }
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    } else if (previo.porcentaje != null && pctActual > previo.porcentaje && faltasActuales == faltasPrevias) {
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró una nueva asistencia! (${formatearPct(pctActual)}%)."
                        publishForCurrentSession { NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje) }
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    }
                }
            }
        }

        notificationPrefs.ultimoSnapshotAsistencia = gson.toJson(nuevoSnapshot)
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

        // Si el snapshot previo nunca guardó subcomponentes (migración desde versión anterior),
        // evitamos disparar una ráfaga de notificaciones de notas pasadas para subcomponentes
        val migrandoDesdeVersionSinSubcomponentes = snapshotPrevio.isNotEmpty() &&
                snapshotPrevio.values.all { it.subcomponentesMap.isEmpty() }

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

            // Comparar solo si ya teníamos un snapshot previo y no es migración inicial sin subcomponentes
            if (snapshotPrevio.isNotEmpty() && !migrandoDesdeVersionSinSubcomponentes) {
                val previo = snapshotPrevio[key]
                if (previo != null) {
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

                                publishForCurrentSession { NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje) }
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
                        val tieneSubs = comp.hasSubComponents && comp.subcomponentes.isNotEmpty()

                        if (!notaActualComp.isNullOrBlank() && notaActualComp != notaPreviaComp) {
                            // Si tiene subcomponentes y ya se notificó un subcomponente, evitamos duplicar con el contenedor
                            if (!tieneSubs || !algunSubNotificado) {
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
                                publishForCurrentSession { NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje) }
                                upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = compNombre)
                            }
                        }
                    }

                    // Caso 3: Cambio en el promedio actual del curso (si no se notificó ningún sub o comp individual)
                    if (!notaActualStr.isNullOrBlank() && notaActualStr != previo.notaActual && !algunSubNotificado && !algunCompNotificado) {
                        val titulo = "🎓 Nota promedio actualizada"
                        val mensaje = "$cursoNombre: Tu promedio actual ahora es $notaActualStr."
                        publishForCurrentSession { NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje) }
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = "Promedio Actual")
                    }
                }
            }
        }

        notificationPrefs.ultimoSnapshotNotas = gson.toJson(nuevoSnapshot)
    }

    private fun formatearPct(pct: Double): String =
        if (pct % 1.0 == 0.0) pct.toInt().toString() else "%.1f".format(pct)

    private data class SnapshotCurso(
        val nombre: String,
        val faltas: Int?,
        val asistencias: Int?,
        val porcentaje: Double?
    )

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

    companion object {
        private const val TAG = "UPAO_BackgroundSync"
        private val syncMutex = com.example.upaos.data.refresh.ForegroundRefreshGate.lock

        fun schedule(context: Context) {
            SyncScheduler.start(context)
        }

        fun scheduleFiveMinuteCheck(context: Context) {
            SyncScheduler.scheduleNext(context)
        }

        fun cancel(context: Context) {
            SyncScheduler.cancel(context)
        }

        fun runOnce(context: Context) {
            SyncScheduler.runNow(context)
        }
    }
}
