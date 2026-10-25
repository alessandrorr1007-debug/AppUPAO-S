package com.example.upaos

import java.io.File
import org.junit.Test
import org.junit.Assert.*

class CanvasReadOnlyTest {
    @Test fun canvasSourcesCannotWrite() {
        val root = sequenceOf(File("src/main/java/com/example/upaos"), File("app/src/main/java/com/example/upaos"))
            .first { it.isDirectory }
        val files = File(root, "data/api").listFiles()!!.filter { it.name.startsWith("Canvas") } +
            File(root, "data/canvas").walkTopDown().filter { it.isFile }.toList()
        val forbidden = Regex("@(?:POST|PUT|DELETE|PATCH|Multipart)\b|MultipartBody")
        assertTrue(files.isNotEmpty())
        files.forEach { assertFalse("Write operation in ${it.name}", forbidden.containsMatchIn(it.readText())) }
    }
}
