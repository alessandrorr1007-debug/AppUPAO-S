package com.example.upaos.data.profile

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

data class ProfileField(val label: String, val value: String)
data class ProfileSection(val title: String, val fields: List<ProfileField>)
data class StudentProfile(
    val fullName: String?,
    val sections: List<ProfileSection>,
    val firstName: String? = null,
    val firstLastName: String? = null
) {
    val shortName: String?
        get() = if (!firstName.isNullOrBlank() && !firstLastName.isNullOrBlank()) "$firstName $firstLastName"
            else shortNameFromFullName(fullName) ?: firstName

    companion object {
        fun parse(json: String): StudentProfile {
            val root = JsonParser.parseString(json)
            require(root.isJsonObject) { "Formato de perfil no válido" }
            val data = root.asJsonObject
            val known = setOf("name", "erpId", "gender", "emails", "addresses", "phones")
            require(known.any(data::has)) { "Respuesta sin campos de perfil" }
            val name = data.get("name")?.takeIf { it.isJsonObject }?.asJsonObject
            val fullName = normalizeName(text(name?.get("fullName")))
            val sections = mutableListOf<ProfileSection>()
            val identity = mutableListOf<ProfileField>()
            fun MutableList<ProfileField>.addValue(label: String, value: String?) {
                value?.takeIf { it.isNotBlank() }?.let { add(ProfileField(label, it)) }
            }
            identity.addValue("Nombre completo", fullName)
            identity.addValue("Nombre(s)", normalizeName(text(name?.get("firstName"))))
            identity.addValue("Segundo nombre", normalizeName(text(name?.get("middleName"))))
            identity.addValue("Apellidos", normalizeName(text(name?.get("lastName"))))
            identity.addValue("Código", text(data.get("erpId")))
            val gender = text(data.get("gender"))
            identity.addValue("Género", when (gender?.uppercase(Locale.ROOT)) {
                "M", "MALE", "MASCULINO", "HOMBRE" -> "Masculino"
                "F", "FEMALE", "FEMENINO", "MUJER" -> "Femenino"
                else -> gender
            })
            if (identity.isNotEmpty()) sections += ProfileSection("Datos del estudiante", identity)
            val emails = array(data.get("emails")).mapNotNull(::text).distinct()
            if (emails.isNotEmpty()) sections += ProfileSection("Correos", emails.mapIndexed { i, value -> ProfileField("Correo ${i + 1}", value) })
            array(data.get("addresses")).forEachIndexed { index, element ->
                if (!element.isJsonObject) return@forEachIndexed
                val address = element.asJsonObject
                val place = address.get("addressPlace")?.takeIf { it.isJsonObject }?.asJsonObject
                val fields = mutableListOf<ProfileField>()
                fields.addValue("Tipo", text(address.get("type")))
                fields.addValue("Calle", array(address.get("addressLines")).mapNotNull(::text).joinToString("\n"))
                fields.addValue("Localidad", text(place?.get("locality")))
                fields.addValue("Región", text(place?.get("region")))
                fields.addValue("País", text(place?.get("country")))
                val extras = JsonObject().apply {
                    address.entrySet().filter { it.key !in setOf("type", "addressLines", "addressPlace") }.forEach { add(it.key, it.value) }
                    place?.entrySet()?.filter { it.key !in setOf("locality", "region", "country") }?.forEach { add(it.key, it.value) }
                }
                flatten(extras, "", fields)
                if (fields.isNotEmpty()) sections += ProfileSection("Dirección ${index + 1}", fields)
            }
            val phones = mutableListOf<ProfileField>()
            flatten(data.get("phones"), "Teléfono", phones)
            if (phones.isNotEmpty()) sections += ProfileSection("Teléfonos", phones)
            val extras = JsonObject().apply {
                data.entrySet().filter { it.key !in known }.forEach { add(it.key, it.value) }
                name?.entrySet()?.filter { it.key !in setOf("fullName", "firstName", "middleName", "lastName") }?.forEach { add(it.key, it.value) }
            }
            val extraFields = mutableListOf<ProfileField>()
            flatten(extras, "", extraFields)
            if (extraFields.isNotEmpty()) sections += ProfileSection("Información adicional", extraFields)
            return StudentProfile(fullName, sections,
                givenName(text(name?.get("firstName"))) ?: givenName(fullName),
                familyName(text(name?.get("lastName"))))
        }

        fun normalizeName(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            val locale = Locale("es", "PE")
            Regex("[\\p{L}\\p{M}]+").replace(name.lowercase(locale).replace(Regex("[\\s\\p{Z}]+"), " ")) {
                it.value.replaceFirstChar { char -> char.titlecase(locale) }
            }.trim().takeIf { it.isNotEmpty() }
        }

        private fun usableName(value: String?): String? = normalizeName(value)?.takeUnless {
            Regex("(?i)(usuario|user|estudiante)(\\s*(upao|[0-9]+))?").matches(it)
        }

        private fun cleanNameWithDelimiters(value: String?): String? = usableName(value)?.let {
            if ('/' in it && ',' !in it) it.replace('/', ',') else it
        }

        fun givenName(value: String?): String? = cleanNameWithDelimiters(value)?.let {
            it.substringAfter(',', it).trim().substringBefore(' ')
        }

        private fun familyName(value: String?): String? {
            val clean = cleanNameWithDelimiters(value) ?: return null
            val words = clean.substringBefore(',').trim().split(' ')
            val particles = setOf("de", "del", "la", "las", "los", "san", "santa", "santo")
            var end = 0
            while (end < words.lastIndex && words[end].lowercase(Locale.ROOT) in particles) end++
            return words.take(end + 1).joinToString(" ")
        }

        fun shortNameFromFullName(value: String?): String? {
            val full = cleanNameWithDelimiters(value) ?: return null
            if (',' in full) {
                val given = givenName(full.substringAfter(','))
                val family = familyName(full.substringBefore(','))
                return listOfNotNull(given, family).joinToString(" ").takeIf { it.isNotBlank() }
            }
            val words = full.split(' ')
            val particles = setOf("de", "del", "la", "las", "los", "san", "santa", "santo")
            return when (words.size) {
                1 -> words.first()
                2 -> full
                else -> {
                    var idx = words.lastIndex - 1
                    while (idx > 1 && words[idx - 1].lowercase(Locale.ROOT) in particles) {
                        idx--
                    }
                    val firstLastName = words.subList(idx, words.lastIndex).joinToString(" ")
                    "${words.first()} $firstLastName"
                }
            }
        }

        private fun text(value: JsonElement?): String? = when {
            value == null || value.isJsonNull -> null
            value.isJsonPrimitive -> value.asString.trim().takeIf { it.isNotEmpty() }
            value.isJsonObject -> text(value.asJsonObject.get("title"))
            else -> null
        }
        private fun array(value: JsonElement?): List<JsonElement> = value?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
        private fun flatten(value: JsonElement?, prefix: String, fields: MutableList<ProfileField>) {
            when {
                value == null || value.isJsonNull -> Unit
                value.isJsonArray -> value.asJsonArray.forEachIndexed { index, item -> flatten(item, "$prefix ${index + 1}".trim(), fields) }
                value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, item) ->
                    val label = when (key) {
                        "type", "phoneType" -> "Tipo"
                        "number", "phoneNumber", "telephoneNumber" -> "Número"
                        "extension" -> "Extensión"
                        "countryCode", "countryCallingCode" -> "Código de país"
                        "primary", "preferred", "isPrimary" -> "Principal"
                        "title" -> "Descripción"
                        "phone" -> "Teléfono"
                        "favoredName" -> "Nombre preferido"
                        "genderIdentity" -> "Identidad de género"
                        "personalPronoun" -> "Pronombre personal"
                        "id" -> "Identificador"
                        else -> "Dato ($key)"
                    }
                    flatten(item, listOf(prefix, label).filter { it.isNotBlank() }.joinToString(" · "), fields)
                }
                else -> text(value)?.let { fields += ProfileField(prefix.ifBlank { "Dato" }, it) }
            }
        }
    }
}
