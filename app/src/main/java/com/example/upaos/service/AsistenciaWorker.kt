package com.example.upaos.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.LoginRequest
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.example.upaos.widget.ResumenNotasWidget
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AsistenciaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationPrefs = NotificationPreferences(context)
    private val tokenManager = TokenManager(context)
    private val apiCache = ApiCache(context)
    private val gradesCache = GradesCache(context)
    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val checkAsistencia = notificationPrefs.checkAsistenciaEnabled
        val checkNotas = notificationPrefs.checkNotasEnabled

        if (!checkAsistencia && !checkNotas) {
            Log.d(TAG, "Notificaciones de asistencia y notas desactivadas. Omitiendo revisión.")
            return@withContext Result.success()
        }

        val user = tokenManager.getSavedUser()
        val pass = tokenManager.getSavedPass()
        var token = tokenManager.getToken()

        if (user.isNullOrBlank()) {
            Log.d(TAG, "Sin usuario guardado, omitiendo chequeo.")
            return@withContext Result.success()
        }

        try {
            // Si no hay token pero sí credenciales, intentamos autenticar
            if (token.isNullOrBlank() && !pass.isNullOrBlank()) {
                val loginRes = RetrofitClient.apiService.login(LoginRequest(user, pass))
                val nuevoToken = loginRes.body()?.token
                if (loginRes.isSuccessful && !nuevoToken.isNullOrBlank()) {
                    token = nuevoToken
                    tokenManager.saveToken(nuevoToken)
                }
            }

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No se pudo obtener token para sincronización en segundo plano.")
                SyncScheduler.scheduleNext(applicationContext, SyncScheduler.INTERVAL_MINUTES)
                return@withContext Result.success()
            }

            // 1. Revisión de Asistencia
            if (checkAsistencia) {
                token = revisarAsistencia(token, user, pass)
            }

            // 2. Revisión de Notas
            if (checkNotas) {
                revisarNotas(token, user, pass)
            }

            // Reprograma la siguiente verificación en 5 minutos
            SyncScheduler.scheduleNext(applicationContext, SyncScheduler.INTERVAL_MINUTES)
            Log.d(TAG, "Sincronización en segundo plano completada con éxito. Próxima en 5 min.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en AsistenciaWorker/BackgroundSync: ${e.localizedMessage}", e)
            SyncScheduler.scheduleNext(applicationContext, SyncScheduler.INTERVAL_MINUTES)
            Result.retry()
        }
    }

    private suspend fun revisarAsistencia(tokenInicial: String, user: String, pass: String?): String {
        var activeToken = tokenInicial
        try {
            var res = RetrofitClient.apiService.getAsistencia("Bearer $activeToken")

            if (res.code() == 401 && !pass.isNullOrBlank()) {
                val loginRes = RetrofitClient.apiService.login(LoginRequest(user, pass))
                val nuevoToken = loginRes.body()?.token
                if (loginRes.isSuccessful && !nuevoToken.isNullOrBlank()) {
                    activeToken = nuevoToken
                    tokenManager.saveToken(nuevoToken)
                    res = RetrofitClient.apiService.getAsistencia("Bearer $activeToken")
                }
            }

            if (res.isSuccessful && res.body() != null) {
                val nuevaAsistencia = res.body()!!.asistencia
                compararYNotificarAsistencia(nuevaAsistencia)

                val cacheKey = "asistencia_$user"
                apiCache.guardar(cacheKey, gson.toJson(res.body()!!))
                notificationPrefs.ultimaRevisionAsistencia = System.currentTimeMillis()
                Log.d(TAG, "Revisión de asistencia completada.")
            } else {
                Log.w(TAG, "Respuesta no exitosa al consultar asistencia: ${res.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error revisando asistencia: ${e.localizedMessage}")
        }
        return activeToken
    }

    private suspend fun revisarNotas(token: String, user: String, pass: String?) {
        var activeToken = token
        try {
            // Determinamos periodo y carrera
            var periodo = "202610"
            var carrera = "UG"

            // Intentamos recuperar del caché guardado por la app
            val cachedJson = gradesCache.cargar(user)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedGrades = gson.fromJson(cachedJson, GradesResponse::class.java)
                    if (!cachedGrades?.periodo.isNullOrBlank()) periodo = cachedGrades.periodo!!
                    if (!cachedGrades?.carrera.isNullOrBlank()) carrera = cachedGrades.carrera!!
                } catch (_: Exception) {}
            } else {
                try {
                    val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $activeToken")
                    if (periodosRes.isSuccessful && periodosRes.body() != null) {
                        val body = periodosRes.body()!!
                        periodo = detectarPeriodoActual(body.periodos, body.periodoActual)
                    }
                } catch (_: Exception) {}
            }

            val req = mapOf("periodo" to periodo, "carrera" to carrera)
            var res = RetrofitClient.apiService.buscarNotas("Bearer $activeToken", req)

            if (res.code() == 401 && !pass.isNullOrBlank()) {
                val loginRes = RetrofitClient.apiService.login(LoginRequest(user, pass))
                val nuevoToken = loginRes.body()?.token
                if (loginRes.isSuccessful && !nuevoToken.isNullOrBlank()) {
                    activeToken = nuevoToken
                    tokenManager.saveToken(nuevoToken)
                    res = RetrofitClient.apiService.buscarNotas("Bearer $activeToken", req)
                }
            }

            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                compararYNotificarNotas(body.cursos)

                // Guardar en caché y actualizar widget de escritorio
                gradesCache.guardar(user, gson.toJson(body))
                notificationPrefs.ultimaRevisionNotas = System.currentTimeMillis()

                try {
                    ResumenNotasWidget.updateAll(applicationContext)
                } catch (_: Exception) {}

                Log.d(TAG, "Revisión de notas completada (${body.cursos.size} cursos analizados).")
            } else {
                Log.w(TAG, "Respuesta no exitosa al consultar notas: ${res.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error revisando notas en segundo plano: ${e.localizedMessage}")
        }
    }

    private fun compararYNotificarAsistencia(nuevosCursos: List<AsistenciaCurso>) {
        val snapshotPrevioRaw = notificationPrefs.ultimoSnapshotAsistencia
        val type = object : TypeToken<Map<String, SnapshotCurso>>() {}.type
        val snapshotPrevio: Map<String, SnapshotCurso> = if (!snapshotPrevioRaw.isNullOrBlank()) {
            try {
                gson.fromJson(snapshotPrevioRaw, type) ?: emptyMap()
            } catch (e: Exception) {
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
                    } else if (asistenciasActuales != null && asistPrevias != null && asistenciasActuales > asistPrevias) {
                        val dif = asistenciasActuales - asistPrevias
                        val plural = if (dif == 1) "asistencia" else "$dif asistencias"
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró tu $plural! Llevas $asistenciasActuales asistencias."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                    } else if (previo.porcentaje != null && pctActual > previo.porcentaje && faltasActuales == faltasPrevias) {
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró una nueva asistencia! (${formatearPct(pctActual)}%)."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                    }
                }
            }
        }

        notificationPrefs.ultimoSnapshotAsistencia = gson.toJson(nuevoSnapshot)
    }

    private fun compararYNotificarNotas(nuevosCursos: List<CourseGrade>) {
        val snapshotPrevioRaw = notificationPrefs.ultimoSnapshotNotas
        val type = object : TypeToken<Map<String, SnapshotNotasCurso>>() {}.type
        val snapshotPrevio: Map<String, SnapshotNotasCurso> = if (!snapshotPrevioRaw.isNullOrBlank()) {
            try {
                gson.fromJson(snapshotPrevioRaw, type) ?: emptyMap()
            } catch (e: Exception) {
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

            // Comparar solo si ya teníamos un snapshot previo (evita inundar de alertas al primer inicio)
            if (snapshotPrevio.isNotEmpty()) {
                val previo = snapshotPrevio[key]
                if (previo != null) {
                    val cursoNombre = curso.displayNombre

                    // Caso 1: Se subió o modificó la nota de EP1
                    if (!ep1NotaStr.isNullOrBlank() && ep1NotaStr != previo.ep1Nota) {
                        val titulo = "🎓 Nueva nota en EP1"
                        val mensaje = "$cursoNombre: Tu nota de EP1 es $ep1NotaStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                    }

                    // Caso 2: Se subió o modificó la nota de EP2
                    if (!ep2NotaStr.isNullOrBlank() && ep2NotaStr != previo.ep2Nota) {
                        val titulo = "🎓 Nueva nota en EP2"
                        val mensaje = "$cursoNombre: Tu nota de EP2 es $ep2NotaStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                    }

                    // Caso 3: Cambio en algún componente evaluativo específico (prácticas, parciales, etc.)
                    for ((compName, compVal) in compMap) {
                        val valPrevio = previo.componentes[compName]
                        if (!compVal.isNullOrBlank() && compVal != valPrevio) {
                            val etiquetaLimpia = compName.replace("EP1_", "EP1 - ").replace("EP2_", "EP2 - ")
                            val titulo = "🎓 Nueva evaluación calificada"
                            val mensaje = "$cursoNombre: Calificación en $etiquetaLimpia: $compVal."
                            NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
                        }
                    }

                    // Caso 4: Cambio en la nota actual o promedio final del curso
                    if (!notaActualStr.isNullOrBlank() && notaActualStr != previo.notaActual &&
                        ep1NotaStr == previo.ep1Nota && ep2NotaStr == previo.ep2Nota
                    ) {
                        val titulo = "🎓 Nota promedio actualizada"
                        val mensaje = "$cursoNombre: Tu promedio actual ahora es $notaActualStr."
                        NotificationService.mostrarNotificacionNotas(applicationContext, titulo, mensaje)
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

        /**
         * Programa la sincronización periódica de 5 minutos utilizando SyncScheduler
         */
        fun schedule(context: Context, intervalMinutes: Long = 5) {
            SyncScheduler.start(context)
        }

        fun scheduleFiveMinuteCheck(context: Context) {
            SyncScheduler.scheduleNext(context, 5L)
        }

        fun cancel(context: Context) {
            SyncScheduler.cancel(context)
        }

        fun runOnce(context: Context) {
            SyncScheduler.runNow(context)
        }
    }
}
