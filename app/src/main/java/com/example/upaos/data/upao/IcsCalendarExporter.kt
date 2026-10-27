package com.example.upaos.data.upao

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.upaos.data.model.HorarioCurso
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Generador y exportador de archivos iCalendar (.ics) compatibles con Google Calendar
 * y otras aplicaciones de calendario según la norma RFC 5545.
 */
object IcsCalendarExporter {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /**
     * Escapa caracteres especiales en textos según la especificación RFC 5545:
     * '\' -> '\\', ';' -> '\;', ',' -> '\,', salto de línea -> '\n'.
     */
    fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }

    /**
     * Aplica el plegado de líneas (line folding) a un máximo de [maxBytes] octetos (UTF-8)
     * por línea conforme a la sección 3.1 de RFC 5545, insertando CRLF seguido de un espacio.
     */
    fun foldLine(line: String, maxBytes: Int = 75): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return line

        val sb = StringBuilder()
        var currentLineBytes = 0
        var isFirst = true

        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val charCount = Character.charCount(cp)
            val charStr = line.substring(i, i + charCount)
            val charBytes = charStr.toByteArray(Charsets.UTF_8).size

            val limit = if (isFirst) maxBytes else maxBytes - 1
            if (currentLineBytes + charBytes > limit && currentLineBytes > 0) {
                sb.append("\r\n ")
                isFirst = false
                currentLineBytes = 1 + charBytes
                sb.append(charStr)
            } else {
                sb.append(charStr)
                currentLineBytes += charBytes
            }
            i += charCount
        }

        return sb.toString()
    }

    /**
     * Genera el contenido completo de un archivo .ics con recurrencia semanal para los cursos dados.
     * Respeta la fusión de bloques continuos y semanas académicas del AcademicCalendar.
     */
    fun generarIcs(
        cursos: List<HorarioCurso>,
        term: String,
        fechaInicioOverride: LocalDate? = null,
        ahoraUtc: ZonedDateTime? = null
    ): String {
        val fechaInicio = fechaInicioOverride ?: com.example.upaos.domain.academic.AcademicRules
            .startDate(term)?.date ?: throw IllegalArgumentException("Periodo académico no válido")

        // Fin de la semana 16 académica (domingo a las 23:59:59 en hora de Lima)
        val finSemana16 = fechaInicio.plusWeeks(AcademicCalendar.TOTAL_SEMANAS_REGULARES.toLong()).minusDays(1)
        val finUtc = ZonedDateTime.of(finSemana16, LocalTime.of(23, 59, 59), ScheduleCalculator.LIMA_ZONE)
            .withZoneSameInstant(ZoneOffset.UTC)
        val untilStr = finUtc.format(UTC_FORMAT)

        val dtstamp = (ahoraUtc ?: ZonedDateTime.now(ZoneOffset.UTC)).format(UTC_FORMAT)

        val lines = mutableListOf<String>()
        lines.add("BEGIN:VCALENDAR")
        lines.add("VERSION:2.0")
        lines.add("PRODID:-//UPAO S//Horario UPAO//ES")
        lines.add("CALSCALE:GREGORIAN")
        lines.add("METHOD:PUBLISH")

        // Definición de Zona Horaria America/Lima
        lines.add("BEGIN:VTIMEZONE")
        lines.add("TZID:America/Lima")
        lines.add("X-LIC-LOCATION:America/Lima")
        lines.add("BEGIN:STANDARD")
        lines.add("TZOFFSETFROM:-0500")
        lines.add("TZOFFSETTO:-0500")
        lines.add("TZNAME:PET")
        lines.add("DTSTART:19700101T000000")
        lines.add("END:STANDARD")
        lines.add("END:VTIMEZONE")

        for (curso in cursos) {
            val bloquesFusionados = ScheduleCalculator.fusionarBloques(curso.bloques)
            val nombreCurso = curso.displayNombre

            for (bloque in bloquesFusionados) {
                val diaIndex = bloque.dia ?: continue
                if (diaIndex !in 0..6) continue
                val horaInicioRaw = bloque.horaInicio?.trim().orEmpty()
                val horaFinRaw = bloque.horaFin?.trim().orEmpty()
                if (horaInicioRaw.length < 4 || horaFinRaw.length < 4) continue

                // Calcular la fecha del primer día de clase respecto al inicio del semestre
                val offsetDias = ((diaIndex - (fechaInicio.dayOfWeek.value - 1)) % 7 + 7) % 7
                val fechaPrimerDia = fechaInicio.plusDays(offsetDias.toLong())

                val hInicio = horaInicioRaw.replace(":", "").padStart(4, '0') + "00"
                val hFin = horaFinRaw.replace(":", "").padStart(4, '0') + "00"
                val dtStart = "${fechaPrimerDia.format(DATE_FORMAT)}T$hInicio"
                val dtEnd = "${fechaPrimerDia.format(DATE_FORMAT)}T$hFin"

                val uid = "upao-${term}-${curso.crn ?: nombreCurso.hashCode()}-${diaIndex}-${hInicio}@upaos"

                val aula = bloque.aula?.trim().orEmpty().ifBlank { "UPAO" }
                val descParts = mutableListOf<String>()
                val docente = bloque.displayDocente ?: curso.displayDocente
                if (!docente.isNullOrBlank()) {
                    descParts.add("Docente: $docente")
                }
                val tipo = bloque.displayTipo ?: bloque.tipo
                if (!tipo.isNullOrBlank()) {
                    descParts.add("Tipo: $tipo")
                }
                val descripcion = descParts.joinToString("\n").ifBlank { "Clase regular UPAO" }

                lines.add("BEGIN:VEVENT")
                lines.add("UID:$uid")
                lines.add("DTSTAMP:$dtstamp")
                lines.add("DTSTART;TZID=America/Lima:$dtStart")
                lines.add("DTEND;TZID=America/Lima:$dtEnd")
                lines.add("RRULE:FREQ=WEEKLY;UNTIL=$untilStr")
                lines.add("SUMMARY:${escapeIcsText(nombreCurso)}")
                lines.add("LOCATION:${escapeIcsText(aula)}")
                lines.add("DESCRIPTION:${escapeIcsText(descripcion)}")
                lines.add("STATUS:CONFIRMED")
                lines.add("END:VEVENT")
            }
        }

        lines.add("END:VCALENDAR")

        // Unir con CRLF y plegar líneas largas a 75 bytes
        return lines.joinToString("\r\n") { foldLine(it) } + "\r\n"
    }

    /**
     * Guarda el archivo .ics en el directorio de caché de la app para compartirlo vía FileProvider.
     */
    fun guardarArchivoIcs(context: Context, cursos: List<HorarioCurso>, term: String): File {
        val icsText = generarIcs(cursos, term)
        val calendarDir = File(context.cacheDir, "calendar")
        if (!calendarDir.exists()) calendarDir.mkdirs()
        val icsFile = File(calendarDir, "horario_upao_$term.ics")
        icsFile.writeText(icsText, Charsets.UTF_8)
        return icsFile
    }

    /**
     * Abre el selector del sistema para agregar el horario al calendario usando ACTION_VIEW
     * o ACTION_SEND con FileProvider (sin requerir permisos de calendario).
     */
    fun exportarAlCalendario(context: Context, cursos: List<HorarioCurso>, term: String) {
        if (cursos.isEmpty()) {
            Toast.makeText(context, "No hay horario cargado todavía para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val file = guardarArchivoIcs(context, cursos, term)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/calendar")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Horario UPAO - Periodo $term")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(viewIntent, "Agregar al calendario con...")
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(sendIntent))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar horario: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
