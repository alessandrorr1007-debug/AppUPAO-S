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
                notificationPrefs.syncConsecutiveFailures = 0
                SyncScheduler.scheduleNext(applicationContext, fallosConsecutivos = 0)
                Log.d(TAG, "Sincronización en segundo plano completada con éxito. Próximo ciclo adaptativo programado.")
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error en AsistenciaWorker/BackgroundSync: ${e.localizedMessage}", e)
                val fallos = notificationPrefs.syncConsecutiveFailures + 1
                notificationPrefs.syncConsecutiveFailures = fallos
                SyncScheduler.scheduleNext(applicationContext, fallosConsecutivos = fallos)
                Result.retry()
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun revisarAsistencia(user: String) {
        try {
            val res = upaoRepository.getAsistencia()
            if (tokenManager.getSavedUser() != user) return

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
            Log.e(TAG, "Error revisando asistencia: ${e.localizedMessage}")
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
            if (tokenManager.getSavedUser() != user) return

            if (grades.cursos.isNotEmpty()) {
                compararYNotificarNotas(grades.cursos, user)

                gradesCache.guardar("notas_$user", gson.toJson(grades))
                notificationPrefs.ultimaRevisionNotas = System.currentTimeMillis()

                try {
                    ResumenNotasWidget.updateAll(applicationContext)
                } catch (_: Exception) {}

                Log.d(TAG, "Revisión de notas completada (${grades.cursos.size} cursos analizados).")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error revisando notas en segundo plano: ${e.localizedMessage}")
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
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    } else if (asistenciasActuales != null && asistPrevias != null && asistenciasActuales > asistPrevias) {
                        val dif = asistenciasActuales - asistPrevias
                        val plural = if (dif == 1) "asistencia" else "$dif asistencias"
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró tu $plural! Llevas $asistenciasActuales asistencias."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    } else if (previo.porcentaje != null && pctActual > previo.porcentaje && faltasActuales == faltasPrevias) {
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró una nueva asistencia! (${formatearPct(pctActual)}%)."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = nombreConTipo, componente = "Asistencia")
                    }
                }
            }
        }

        notificationPrefs.ultimoSnapshotAsistencia = gson.toJson(nuevoSnapshot)
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
            val notaActualStr = curso.displayNotaActual?.toString()?.trim()
            val ep1NotaStr = curso.displayEp1.nota?.toString()?.trim()
            val ep2NotaStr = curso.displayEp2.nota?.toString()?.trim()

            val compMap = mutableMapOf<String, String?>()
            curso.displayEp1.detalles.forEach { d ->
                val comp = d.componente?.trim()
                val nota = d.nota?.toString()?.trim()
                if (!comp.isNullOrBlank() && !nota.isNullOrBlank()) {
                    compMap["EP1_$comp"] = nota
                }
            }
            curso.displayEp2.detalles.forEach { d ->
                val comp = d.componente?.trim()
                val nota = d.nota?.toString()?.trim()
                if (!comp.isNullOrBlank() && !nota.isNullOrBlank()) {
                    compMap["EP2_$comp"] = nota
                }
            }

            nuevoSnapshot[key] = SnapshotNotasCurso(
                nombre = curso.displayNombre,
                notaActual = notaActualStr,
                ep1Nota = ep1NotaStr,
                ep2Nota = ep2NotaStr,
                componentes = compMap
            )

            // Comparar solo si ya teníamos un snapshot previo
            if (snapshotPrevio.isNotEmpty()) {
                val previo = snapshotPrevio[key]
                if (previo != null) {
                    val cursoNombre = curso.displayNombre

                    // Caso 1: Se subió o modificó la nota de EP1
                    if (!ep1NotaStr.isNullOrBlank() && ep1NotaStr != previo.ep1Nota) {
                        val titulo = "🎓 Nueva nota en EP1"
                        val mensaje = "$cursoNombre: Tu nota de EP1 es $ep1NotaStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = "EP1")
                    }

                    // Caso 2: Se subió o modificó la nota de EP2
                    if (!ep2NotaStr.isNullOrBlank() && ep2NotaStr != previo.ep2Nota) {
                        val titulo = "🎓 Nueva nota en EP2"
                        val mensaje = "$cursoNombre: Tu nota de EP2 es $ep2NotaStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                        upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = "EP2")
                    }

                    // Caso 3: Cambio en algún componente evaluativo específico
                    for ((compName, compVal) in compMap) {
                        val valPrevio = previo.componentes[compName]
                        if (!compVal.isNullOrBlank() && compVal != valPrevio) {
                            val etiquetaLimpia = compName.replace("EP1_", "EP1 - ").replace("EP2_", "EP2 - ")
                            val titulo = "🎓 Nueva evaluación calificada"
                            val mensaje = "$cursoNombre: Calificación en $etiquetaLimpia: $compVal."
                            NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                            upaoRepository.registrarNotificacionLocal(user, mensaje, curso = cursoNombre, componente = etiquetaLimpia)
                        }
                    }

                    // Caso 4: Cambio en la nota actual o promedio final del curso
                    if (!notaActualStr.isNullOrBlank() && notaActualStr != previo.notaActual &&
                        ep1NotaStr == previo.ep1Nota && ep2NotaStr == previo.ep2Nota
                    ) {
                        val titulo = "🎓 Nota promedio actualizada"
                        val mensaje = "$cursoNombre: Tu promedio actual ahora es $notaActualStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
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
        val ep1Nota: String?,
        val ep2Nota: String?,
        val componentes: Map<String, String?> = emptyMap()
    )

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
