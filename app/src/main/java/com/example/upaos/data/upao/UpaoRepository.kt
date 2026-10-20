package com.example.upaos.data.upao

import android.content.Context
import com.example.upaos.data.refresh.*
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.AsistenciaResponse
import com.example.upaos.data.model.CarrerasResponse
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.DetalleCursoResponse
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.data.model.NotificacionesResponse
import com.example.upaos.data.model.PeriodosResponse
import com.example.upaos.data.model.PromedioCursoItem
import com.example.upaos.data.model.PromedioPeriodoResponse
import com.example.upaos.data.model.SemanaInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class UpaoRepository(private val context: Context) {

    private val bannerApi = BannerApi(context)
    private val apiCache = ApiCache(context)
    private val gradesCache = GradesCache(context)
    private val tokenManager = TokenManager(context)
    private val gson = Gson()

    private val currentUser: String = tokenManager.getSavedUser().orEmpty()

    suspend fun getPeriodos(): PeriodosResponse = withContext(Dispatchers.IO) {
        val periodos = bannerApi.getPeriodos()
        val current = Calculators.detectarPeriodoActual(periodos)
        PeriodosResponse(periodoActual = current, periodos = periodos)
    }

    suspend fun getCarreras(term: String): CarrerasResponse = withContext(Dispatchers.IO) {
        val niveles = bannerApi.getNiveles(term)
        CarrerasResponse(carreras = niveles)
    }

    suspend fun getDetalleCurso(term: String, carrera: String, crn: String): DetalleCursoResponse = withContext(Dispatchers.IO) {
        val detail = bannerApi.getCourseGradeDetail(term, crn)
        detail
    }

    suspend fun buscarNotas(term: String, carrera: String): GradesResponse = buscarNotasResult(term, carrera).requireComplete()

    suspend fun buscarNotasResult(term: String, carrera: String): RefreshResult<GradesResponse> = captureRefresh("Notas de Banner") {
        buscarNotasCompletas(term, carrera)
    }

    private suspend fun buscarNotasCompletas(term: String, carrera: String): GradesResponse = withContext(Dispatchers.IO) {
        val courses = bannerApi.getCourses(term, carrera)

        val projections = java.util.concurrent.ConcurrentHashMap<String, String>()

        // Enriquecer cada curso con su detalle y nota actual progresiva en paralelo (limitado por Semaphore en BannerApi)
        val cursosEnriquecidos = coroutineScope {
            courses.map { curso ->
                async {
                    val crn = curso.crn ?: curso.courseReferenceNumber
                    if (crn.isNullOrBlank()) {
                        throw UpaoUnexpectedFormatException("Falta el identificador de un curso")
                    } else {
                        try {
                            val detail = bannerApi.getCourseGradeDetail(term, crn)
                            val notaActual = Calculators.calcularNotaActual(detail.detalles)
                            detail.notaProyectada?.let { projections[crn] = it.toString() }

                            val compEp1 = detail.detalles.firstOrNull { c ->
                                val t = listOfNotNull(c.nombre, c.description, c.codigo).joinToString(" ").uppercase(Locale.ROOT)
                                t.contains("EP1") || t.contains("PROCESO 1") || t.contains("PERMANENTE 1") || t.contains("EP 1")
                            }
                            val compEp2 = detail.detalles.firstOrNull { c ->
                                val t = listOfNotNull(c.nombre, c.description, c.codigo).joinToString(" ").uppercase(Locale.ROOT)
                                t.contains("EP2") || t.contains("PROCESO 2") || t.contains("PERMANENTE 2") || t.contains("EP 2")
                            }
                            val gradeSectionEp1 = com.example.upaos.data.model.GradeSection(nota = compEp1?.displayNota)
                            val gradeSectionEp2 = com.example.upaos.data.model.GradeSection(nota = compEp2?.displayNota)

                            curso.copy(
                                notaActual = notaActual,
                                componentes = detail.detalles,
                                ep1 = gradeSectionEp1,
                                ep2 = gradeSectionEp2
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { throw IncompleteRefreshException(listOf(RefreshIssue("Desglose de ${curso.displayNombre}", e))) }
                    }
                }
            }.awaitAll()
        }

        // Keep the legacy projection cache, but only after every course detail completed.
        for ((crn, value) in projections) {
            if (currentUser.isNotBlank()) gradesCache.guardar("proyectada_${currentUser}_$crn", value)
        }
        val notasValidas = cursosEnriquecidos.mapNotNull { Calculators.toDouble(it.notaActual) }
        val promedioGeneral: Double? = if (notasValidas.isNotEmpty()) {
            val prom = notasValidas.average()
            (prom * 100.0).roundToInt() / 100.0
        } else {
            null
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val fechaActualizacion = dateFormat.format(Date())

        GradesResponse(
            periodo = term,
            carrera = carrera,
            ultimaActualizacion = fechaActualizacion,
            cursos = cursosEnriquecidos,
            promedioGeneral = promedioGeneral,
            promedioBasadoEn = "nota_actual"
        )
    }

    suspend fun getPromedioPeriodo(term: String, cursosCandidatos: List<CourseGrade>? = null): PromedioPeriodoResponse = withContext(Dispatchers.IO) {
        val cursos = cursosCandidatos ?: bannerApi.getCourses(term)

        val items = mutableListOf<PromedioCursoItem>()
        var sumaCreditos = 0

        for (curso in cursos) {
            val crn = curso.crn ?: curso.courseReferenceNumber ?: continue
            val nota = Calculators.toDouble(curso.notaActual)

            // Extraer créditos desde rawBanner o metadata del curso
            val raw = curso.rawBanner
            val credVal = raw?.get("creditHours")
                ?: raw?.get("creditHourLow")
                ?: raw?.get("credits")
                ?: raw?.get("hoursAttempted")

            val creditos = Calculators.toDouble(credVal)?.roundToInt() ?: 0

            if (creditos > 0) {
                sumaCreditos += creditos
            }

            items.add(
                PromedioCursoItem(
                    crn = crn,
                    nombre = curso.displayNombre,
                    nota = nota?.toFloat(),
                    creditos = if (creditos > 0) creditos else null
                )
            )
        }

        val pps = Calculators.calcularPps(items)

        PromedioPeriodoResponse(
            periodo = term,
            ppsOficial = null,
            ppsCalculado = pps?.toFloat(),
            fuente = "calculado",
            totalCreditos = if (sumaCreditos > 0) sumaCreditos else null,
            cursos = items
        )
    }

    suspend fun getHorario(term: String): HorarioResponse = withContext(Dispatchers.IO) {
        bannerApi.getHorario(term)
    }

    fun getSemana(term: String, fechaReferencia: Date = Date()): SemanaInfo {
        return AcademicCalendar.calcularSemana(term, fechaReferencia)
    }

    suspend fun getAsistencia(): AsistenciaResponse = withContext(Dispatchers.IO) {
        getAsistenciaResult().requireComplete()
    }

    suspend fun getAsistenciaResult(): RefreshResult<AsistenciaResponse> = captureRefresh("Asistencia de Banner") {
        bannerApi.getAsistencia().also { if (!it.success) throw UpaoUnexpectedFormatException("Consulta de asistencia no exitosa") }
    }

    suspend fun getNotificaciones(usuario: String): NotificacionesResponse = withContext(Dispatchers.IO) {
        val json = apiCache.cargar("notificaciones_$usuario")
        if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, NotificacionesResponse::class.java)
            } catch (_: Exception) {
                NotificacionesResponse()
            }
        } else {
            NotificacionesResponse()
        }
    }

    suspend fun registrarNotificacionLocal(
        usuario: String,
        mensaje: String,
        curso: String? = null,
        componente: String? = null
    ): NotificacionItem = withContext(Dispatchers.IO) {
        val actual = getNotificaciones(usuario)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val fecha = sdf.format(Date())
        val nuevoId = System.currentTimeMillis()
        val nuevoItem = NotificacionItem(
            id = nuevoId,
            mensaje = mensaje,
            curso = curso,
            componente = componente,
            fechaCreacion = fecha,
            leida = false
        )
        val nuevaLista = listOf(nuevoItem) + actual.notificaciones
        val nuevoResponse = NotificacionesResponse(
            noLeidas = nuevaLista.count { !it.leida },
            total = nuevaLista.size,
            notificaciones = nuevaLista
        )
        apiCache.guardar("notificaciones_$usuario", gson.toJson(nuevoResponse))
        nuevoItem
    }

    suspend fun marcarNotificacionLeida(id: Long, usuario: String) = withContext(Dispatchers.IO) {
        val actual = getNotificaciones(usuario)
        val nuevaLista = actual.notificaciones.map {
            if (it.id == id) it.copy(leida = true) else it
        }
        val nuevoResponse = NotificacionesResponse(
            noLeidas = nuevaLista.count { !it.leida },
            total = nuevaLista.size,
            notificaciones = nuevaLista
        )
        apiCache.guardar("notificaciones_$usuario", gson.toJson(nuevoResponse))
    }

    suspend fun marcarTodasNotificacionesLeidas(usuario: String) = withContext(Dispatchers.IO) {
        val actual = getNotificaciones(usuario)
        val nuevaLista = actual.notificaciones.map { it.copy(leida = true) }
        val nuevoResponse = NotificacionesResponse(
            noLeidas = 0,
            total = nuevaLista.size,
            notificaciones = nuevaLista
        )
        apiCache.guardar("notificaciones_$usuario", gson.toJson(nuevoResponse))
    }
}
