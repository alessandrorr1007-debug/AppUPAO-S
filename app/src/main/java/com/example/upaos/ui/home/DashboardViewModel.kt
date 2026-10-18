package com.example.upaos.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.agenda.AgendaItem
import com.example.upaos.data.agenda.AgendaRepository
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.ForegroundRefreshGate
import com.example.upaos.data.refresh.SharedRefreshLoop
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class DashboardState(
    val periods: List<String> = emptyList(),
    val currentPeriod: String = "202610",
    val gradePeriod: String = "202610",
    val career: String = "UG",
    val careers: List<String> = listOf("UG"),
    val schedulePeriod: String = "202610",
    val attendancePeriod: String = "202620",
    val grades: GradesResponse? = null,
    val average: PromedioPeriodoResponse? = null,
    val projected: Map<String, Any?> = emptyMap(),
    val schedules: Map<String, HorarioResponse> = emptyMap(),
    val attendance: AsistenciaResponse? = null,
    val week: SemanaInfo? = null,
    val notices: NotificacionesResponse? = null,
    val agenda: List<AgendaItem> = emptyList(),
    val times: Map<String, Long?> = emptyMap(),
    val fresh: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false
)

class DashboardViewModel(app: Application, private val token: String, private val user: String) : AndroidViewModel(app) {
    private val cache = ApiCache(app)
    private val gradesCache = GradesCache(app)
    private val agendaRepository = AgendaRepository(app)
    private val upaoRepository = com.example.upaos.data.upao.UpaoRepository(app)
    private val gson = Gson()
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    private var loaded = false
    private var gradeChosen = false
    private var scheduleChosen = false
    private var attendanceChosen = false
    private val gradeVariants = mutableMapOf<Pair<String, String>, GradesResponse>()
    private val averages = mutableMapOf<Pair<String, String>, PromedioPeriodoResponse>()
    private val projections = mutableMapOf<Pair<String, String>, Map<String, Any?>>()
    private var selectionVersion = 0
    private val loop = SharedRefreshLoop(viewModelScope, { refreshRound() })
    val gradeCacheKey get() = "notas_$user"
    val attendanceCacheKey get() = "asistencia_$user"
    fun scheduleCacheKey(term: String) = "horario_${user}_$term"

    fun setForeground(active: Boolean) {
        ForegroundRefreshGate.active = active
        loop.setForeground(active)
    }
    fun refresh() { loop.requestRefresh() }

    fun selectGrades(term: String, career: String? = null) {
        gradeChosen = true
        selectionVersion++
        val level = career ?: state.value.career
        mutable.value = state.value.copy(gradePeriod = term, career = level,
            grades = gradeVariants[term to level], average = averages[term to level], projected = projections[term to level] ?: emptyMap())
        if (state.value.grades == null) refreshSelection()
    }
    fun selectSchedule(term: String) {
        scheduleChosen = true
        selectionVersion++
        mutable.value = state.value.copy(schedulePeriod = term)
        ensureSchedule(term)
    }
    fun selectAttendance(term: String) {
        attendanceChosen = true
        selectionVersion++
        mutable.value = state.value.copy(attendancePeriod = term)
        ensureSchedule(term)
    }
    private fun ensureSchedule(term: String) {
        if (term !in state.value.schedules) refreshSelection()
    }
    private fun refreshSelection() {
        viewModelScope.launch {
            val version = selectionVersion
            loop.requestRefresh().join()
            // A user selection made during a round is handled once, centrally, after that round.
            if (version > completedSelectionVersion && !state.value.busy) loop.requestRefresh()
        }
    }
    private var completedSelectionVersion = 0

    private suspend inline fun <reified T> cached(key: String): T? = try {
        cache.cargar(key)?.let { gson.fromJson(it, T::class.java) }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun loadCache() {
        if (loaded) return
        val savedGrades = try { gradesCache.cargar(gradeCacheKey)?.let { gson.fromJson(it, GradesResponse::class.java) } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val periods = cached<PeriodosResponse>("dashboard_periodos_$user")
        val current = detectarPeriodoActual(periods?.periodos ?: emptyList(), periods?.periodoActual)
        val schedules = cache.listarPorPrefijo("horario_${user}_").mapNotNull { (key, json) ->
            val term = key.removePrefix("horario_${user}_")
            if (term.toIntOrNull() == null) null else try { term to gson.fromJson(json, HorarioResponse::class.java) }
            catch (_: Exception) { null }
        }.toMap().toMutableMap()
        val lastTerm = cache.cargar("horario_ultimo_periodo_$user") ?: current
        cached<HorarioResponse>("horario_ultimo_$user")?.let { schedules.putIfAbsent(lastTerm, it) }
        val agenda = agendaRepository.cached(user)
        val times = mutableMapOf<String, Long?>()
        for (key in listOf("agenda_$user", "notificaciones_$user", attendanceCacheKey) + schedules.keys.map(::scheduleCacheKey)) {
            times[key] = cache.actualizado(key)
        }
        val savedProjected = mutableMapOf<String, Any?>()
        savedGrades?.cursos?.forEach { c ->
            val crn = c.crn ?: c.courseReferenceNumber ?: return@forEach
            try {
                gradesCache.cargar("proyectada_${user}_$crn")?.let { savedProjected[crn] = it }
            } catch (_: Exception) {}
        }
        savedGrades?.let {
            val key = (it.periodo ?: current) to (it.carrera ?: "UG")
            gradeVariants[key] = it
            projections[key] = savedProjected
        }
        mutable.value = state.value.copy(periods = periods?.periodos ?: emptyList(), currentPeriod = current,
            gradePeriod = savedGrades?.periodo ?: current, career = savedGrades?.carrera ?: "UG", grades = savedGrades,
            projected = savedProjected,
            schedulePeriod = lastTerm, attendance = cached(attendanceCacheKey), schedules = schedules,
            notices = cached("notificaciones_$user"), agenda = agenda.first, times = times,
            week = cached("semana_$user"))
        loaded = true
    }

    private suspend fun refreshRound() = ForegroundRefreshGate.lock.withLock {
        loadCache()
        val version = selectionVersion
        mutable.value = state.value.copy(busy = true, errors = emptyMap())
        var result = state.value
        val errors = mutableMapOf<String, String>()
        val fresh = mutableSetOf<String>()
        val times = result.times.toMutableMap()
        suspend fun attempt(key: String, action: suspend () -> Unit) {
            try { action(); fresh += key }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors[key] = e.message ?: "Actualización pendiente" }
        }
        suspend fun save(key: String, value: Any) {
            cache.guardar(key, gson.toJson(value)); times[key] = cache.actualizado(key)
        }
        try {
            attempt("periodos") {
                val periods = upaoRepository.getPeriodos()
                val current = detectarPeriodoActual(periods.periodos, periods.periodoActual)
                result = result.copy(periods = periods.periodos, currentPeriod = current,
                    gradePeriod = if (gradeChosen) result.gradePeriod else current,
                    schedulePeriod = if (scheduleChosen) result.schedulePeriod else current,
                    attendancePeriod = if (attendanceChosen) result.attendancePeriod else current)
                save("dashboard_periodos_$user", periods)
                cache.guardar("periodos_$user", gson.toJson(periods.periodos))
            }
            attempt("carreras") {
                val careers = upaoRepository.getCarreras(result.gradePeriod).carreras
                if (careers.isNotEmpty()) result = result.copy(careers = careers,
                    career = result.career.takeIf { it in careers } ?: careers.first())
            }
            attempt(gradeCacheKey) {
                val grades = upaoRepository.buscarNotas(result.gradePeriod, result.career)
                gradeVariants[result.gradePeriod to result.career] = grades
                result = result.copy(grades = grades)
                gradesCache.guardar(gradeCacheKey, gson.toJson(grades))
                times[gradeCacheKey] = gradesCache.observarFecha(gradeCacheKey).first()
                for (course in grades.cursos) {
                    val crn = course.crn ?: course.courseReferenceNumber ?: continue
                    attempt("proyectada_$crn") {
                        val detail = upaoRepository.getDetalleCurso(result.gradePeriod, result.career, crn)
                        val compEvaluados = detail.detalles.filter { !isPendiente(it.displayNota) }
                        val sumaPonderada = if (compEvaluados.isNotEmpty()) {
                            compEvaluados.sumOf { comp ->
                                val nota = comp.displayNota?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                                val peso = comp.displayPeso?.toString()?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
                                nota * (peso / 100.0)
                            }
                        } else null
                        val puntosCalculados = detail.notaProyectada ?: sumaPonderada
                        result = result.copy(projected = result.projected + (crn to puntosCalculados))
                        puntosCalculados?.let { gradesCache.guardar("proyectada_${user}_$crn", it.toString()) }
                    }
                }
            }
            attempt("promedio") {
                val avg = upaoRepository.getPromedioPeriodo(result.gradePeriod, result.grades?.cursos)
                result = result.copy(average = avg)
            }
            result.average?.let { averages[result.gradePeriod to result.career] = it }
            projections[result.gradePeriod to result.career] = result.projected
            attempt(attendanceCacheKey) {
                val attendance = upaoRepository.getAsistencia()
                if (attendance.success || attendance.asistencia.isNotEmpty()) {
                    result = result.copy(attendance = attendance)
                    save(attendanceCacheKey, attendance)
                }
            }
            val schedules = result.schedules.toMutableMap()
            // Today, schedule and attendance reuse each distinct term's ONE response.
            for (term in setOf(result.currentPeriod, result.schedulePeriod, result.attendancePeriod)) {
                val key = scheduleCacheKey(term)
                attempt(key) {
                    val schedule = upaoRepository.getHorario(term)
                    if (schedule.success || schedule.cursos.isNotEmpty()) {
                        schedules[term] = schedule
                        save(key, schedule)
                        if (term == result.currentPeriod) {
                            save("horario_ultimo_$user", schedule)
                            cache.guardar("horario_ultimo_periodo_$user", term)
                            com.example.upaos.service.ClassReminderScheduler.reprogramar(getApplication())
                        }
                    }
                }
            }
            result = result.copy(schedules = schedules)
            attempt("semana") {
                val week = upaoRepository.getSemana(result.currentPeriod)
                result = result.copy(week = week)
                save("semana_$user", week)
            }
            attempt("notificaciones_$user") {
                val notices = upaoRepository.getNotificaciones(user)
                result = result.copy(notices = notices)
                save("notificaciones_$user", notices)
            }
            attempt("agenda_$user") {
                val agenda = agendaRepository.refresh(user); result = result.copy(agenda = agenda)
                times["agenda_$user"] = agendaRepository.cached(user).second
            }
            // Publish the whole round together. Preserve selections changed while requests were in flight.
            val chosen = state.value
            val gradePeriod = if (gradeChosen) chosen.gradePeriod else result.gradePeriod
            val career = if (selectionVersion != version && gradeChosen) chosen.career else result.career
            val sameGrades = gradePeriod == result.gradePeriod && career == result.career
            mutable.value = result.copy(busy = false, fresh = fresh, times = times, errors = errors,
                gradePeriod = gradePeriod, career = career,
                grades = gradeVariants[gradePeriod to career],
                average = if (sameGrades) result.average else averages[gradePeriod to career],
                projected = if (sameGrades) result.projected else projections[gradePeriod to career] ?: emptyMap(),
                schedulePeriod = if (scheduleChosen) chosen.schedulePeriod else result.schedulePeriod,
                attendancePeriod = if (attendanceChosen) chosen.attendancePeriod else result.attendancePeriod)
            completedSelectionVersion = version
            try { com.example.upaos.widget.ResumenNotasWidget.updateAll(getApplication()) } catch (_: Exception) { }
            try { com.example.upaos.widget.ProximoCursoWidget.updateAll(getApplication()) } catch (_: Exception) { }
        } finally { if (state.value.busy) mutable.value = state.value.copy(busy = false) }
    }

    companion object {
        fun factory(app: Application, token: String, user: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(app, token, user) as T
        }
    }
}
