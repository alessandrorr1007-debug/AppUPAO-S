package com.example.upaos

import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.DetalleCursoResponse
import com.example.upaos.data.upao.Calculators
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class BannerApiParsingTest {

    @Test
    fun testParseCoursesJson() {
        val rawJson = """
            {
                "success": true,
                "totalCount": 2,
                "data": [
                    {
                        "courseReferenceNumber": "3233",
                        "courseTitle": "METODOLOGIA DE LA INVESTIGACION CIENTIFICA",
                        "subjectCode": "HUMA",
                        "courseNumber": "1185",
                        "creditHours": 3.0,
                        "finalGrade": null,
                        "midtermGrade": "15"
                    },
                    {
                        "courseReferenceNumber": "4102",
                        "courseTitle": "REDES Y CONECTIVIDAD",
                        "subjectCode": "INGE",
                        "courseNumber": "2040",
                        "creditHours": 4.0,
                        "finalGrade": "16",
                        "midtermGrade": "16"
                    }
                ]
            }
        """.trimIndent()

        val parsed = JsonParser.parseString(rawJson).asJsonObject
        val dataArray = parsed.getAsJsonArray("data")
        assertNotNull(dataArray)
        assertEquals(2, dataArray.size())

        val first = dataArray[0].asJsonObject
        assertEquals("3233", first.get("courseReferenceNumber").asString)
        assertEquals("METODOLOGIA DE LA INVESTIGACION CIENTIFICA", first.get("courseTitle").asString)
        assertEquals(3.0, first.get("creditHours").asDouble, 0.001)
    }

    @Test
    fun testParseComponentDetailsJson() {
        val rawJson = """
            {
                "success": true,
                "totalCount": 2,
                "data": [
                    {
                        "componentId": 10421,
                        "name": "EVALUACION PERMANENTE 1",
                        "percentage": 20.0,
                        "score": 16.0,
                        "hasSubComponents": true
                    },
                    {
                        "componentId": 10422,
                        "name": "EXAMEN PARCIAL",
                        "percentage": 30.0,
                        "score": 14.0,
                        "hasSubComponents": false
                    }
                ]
            }
        """.trimIndent()

        val parsed = JsonParser.parseString(rawJson).asJsonObject
        val dataArray = parsed.getAsJsonArray("data")
        assertEquals(2, dataArray.size())

        val comp1 = dataArray[0].asJsonObject
        assertEquals(10421L, comp1.get("componentId").asLong)
        assertEquals("EVALUACION PERMANENTE 1", comp1.get("name").asString)
        assertEquals(20.0, comp1.get("percentage").asDouble, 0.001)
        assertEquals(16.0, comp1.get("score").asDouble, 0.001)
        assertTrue(comp1.get("hasSubComponents").asBoolean)
    }
}
