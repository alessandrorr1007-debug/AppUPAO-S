package com.example.upaos.service

import java.time.LocalTime

object SyncPolicy {
    val SEMANAS_DE_NOTAS: Set<Int> = setOf(7, 8, 9, 15, 16, 17)
    enum class Mode { INTENSIVO, NORMAL }
    enum class Preference { AUTOMATICO, SIEMPRE_INTENSIVO, NORMAL }
    fun modo(semana: Int?, horaLima: LocalTime, preferencia: Preference): Mode = when (preferencia) {
        Preference.AUTOMATICO -> modo(semana, horaLima)
        Preference.SIEMPRE_INTENSIVO -> Mode.INTENSIVO
        Preference.NORMAL -> Mode.NORMAL
    }
    fun modo(semana: Int?, horaLima: LocalTime, semanas: Set<Int> = SEMANAS_DE_NOTAS): Mode =
        if (semana in semanas && !horaLima.isBefore(LocalTime.of(7, 0)) && horaLima.isBefore(LocalTime.of(23, 0)))
            Mode.INTENSIVO else Mode.NORMAL
}
