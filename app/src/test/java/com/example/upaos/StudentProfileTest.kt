package com.example.upaos

import com.example.upaos.data.profile.StudentProfile
import org.junit.Assert.*
import org.junit.Test

class StudentProfileTest {
    @Test fun normalizesNamesAndCollapsesUnicodeWhitespace() {
        assertEquals("Nombre Apellido", StudentProfile.normalizeName("  NOMBRE   APELLIDO  "))
        assertEquals("José Muñoz", StudentProfile.normalizeName("JOSÉ\u00a0\u00a0MUÑOZ"))
        assertEquals("María-José", StudentProfile.normalizeName("MARÍA-JOSÉ"))
        assertNull(StudentProfile.normalizeName("   "))
    }
    @Test fun nullAndEmptyFieldsAreHidden() {
        val profile = StudentProfile.parse("""{"name":{"fullName":null,"firstName":""},"erpId":null,"gender":" ","emails":[null,""],"addresses":[null,{}],"phones":null}""")
        assertNull(profile.fullName)
        assertTrue(profile.sections.isEmpty())
    }
    @Test fun rendersKnownFieldsAndPhoneObjectsWithoutLosingAdditionalValues() {
        val profile = StudentProfile.parse("""{
          "name":{"fullName":" NOMBRE   DE PRUEBA ","firstName":"NOMBRE","middleName":"DE","lastName":"PRUEBA"},
          "erpId":"000000001","gender":"F","emails":["test@example.invalid","",null],
          "addresses":[{"type":"Prueba","addressLines":["Calle de prueba","",null],"addressPlace":{"locality":"Localidad de prueba","region":{"title":"Región de prueba"},"country":{"title":"País de prueba"}}}],
          "phones":[{"type":"Prueba","number":"000000000","extension":"0"}],"extraField":"synthetic"
        }""")
        assertEquals("Nombre De Prueba", profile.fullName)
        val fields = profile.sections.flatMap { it.fields }
        assertTrue(fields.any { it.label == "Código" && it.value == "000000001" })
        assertTrue(fields.any { it.label == "Género" && it.value == "Femenino" })
        assertTrue(fields.any { it.label == "País" && it.value == "País de prueba" })
        assertTrue(fields.any { it.label.endsWith("Número") && it.value == "000000000" })
        assertTrue(fields.any { it.value == "synthetic" })
        assertFalse(fields.any { it.value.isBlank() || it.value == "null" })
    }
    @Test fun missingFullNameDoesNotInventDrawerName() {
        val profile = StudentProfile.parse("""{"name":{"firstName":"NOMBRE"},"emails":[],"phones":"000000000"}""")
        assertNull(profile.fullName)
        assertTrue(profile.sections.flatMap { it.fields }.any { it.label == "Nombre(s)" })
        assertTrue(profile.sections.flatMap { it.fields }.any { it.label == "Teléfono" })
    }
    @Test fun labelsAdditionalEndpointFieldsInSpanish() {
        val profile = StudentProfile.parse("""{"name":null,"favoredName":"synthetic","genderIdentity":"synthetic","personalPronoun":"synthetic","phone":"000000000","id":"synthetic"}""")
        val labels = profile.sections.flatMap { it.fields }.map { it.label }
        assertTrue(labels.containsAll(listOf("Nombre preferido", "Identidad de género", "Pronombre personal", "Teléfono", "Identificador")))
    }
    @Test fun separatesFirstGivenNameAndFirstLastNameFromStructuredFields() {
        val profile = StudentProfile.parse("""{"name":{"fullName":"NOMBRE SEGUNDO APELLIDO OTRO","firstName":"NOMBRE SEGUNDO","lastName":"APELLIDO OTRO"}}""")
        assertEquals("Nombre", profile.firstName)
        assertEquals("Apellido", profile.firstLastName)
        assertEquals("Nombre Apellido", profile.shortName)
    }
    @Test fun keepsCompoundFirstLastNamesTogether() {
        val profile = StudentProfile.parse("""{"name":{"firstName":"NOMBRE SEGUNDO","lastName":"DE LA CRUZ OTRO"}}""")
        assertEquals("De La Cruz", profile.firstLastName)
        assertEquals("Nombre De La Cruz", profile.shortName)
    }
    @Test fun legacyDisplayNamesHaveSafeShortFallbacks() {
        assertEquals("Nombre Apellido", StudentProfile.shortNameFromFullName("NOMBRE SEGUNDO APELLIDO OTRO"))
        assertEquals("Nombre Apellido", StudentProfile.shortNameFromFullName("APELLIDO OTRO, NOMBRE SEGUNDO"))
        assertEquals("Alessandro Rodriguez", StudentProfile.shortNameFromFullName("RODRIGUEZ RUIZ, ALESSANDRO DANIEL"))
        assertEquals("Alessandro Rodriguez", StudentProfile.shortNameFromFullName("RODRIGUEZ RUIZ/ALESSANDRO"))
        assertEquals("Alessandro Rodriguez", StudentProfile.shortNameFromFullName("RODRIGUEZ RUIZ / ALESSANDRO DANIEL"))
        assertEquals("Alessandro De La Cruz", StudentProfile.shortNameFromFullName("ALESSANDRO DE LA CRUZ RUIZ"))
        assertEquals("Nombre", StudentProfile.givenName("APELLIDO OTRO, NOMBRE SEGUNDO"))
        assertEquals("Alessandro", StudentProfile.givenName("RODRIGUEZ RUIZ, ALESSANDRO"))
        assertEquals("Alessandro", StudentProfile.givenName("RODRIGUEZ RUIZ/ALESSANDRO"))
        assertEquals("Alessandro", StudentProfile.givenName("ALESSANDRO RODRIGUEZ"))
        assertNull(StudentProfile.shortNameFromFullName("Usuario 1"))
        assertNull(StudentProfile.givenName("Usuario UPAO"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsJsonErrorInsteadOfCachingItAsProfile() {
        StudentProfile.parse("""{"error":"synthetic error"}""")
    }
}
