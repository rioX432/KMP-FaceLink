package io.github.kmpfacelink.llm.internal

import java.io.File

internal actual object FileSystem {
    actual fun fileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else -1L
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun mkdirs(path: String) {
        File(path).mkdirs()
    }

    actual fun listFiles(directory: String): List<String> {
        return File(directory).listFiles()?.map { it.name } ?: emptyList()
    }

    actual fun readText(path: String): String? {
        val file = File(path)
        return if (file.exists()) file.readText() else null
    }

    actual fun writeText(path: String, content: String) {
        File(path).writeText(content)
    }

    actual fun rename(source: String, destination: String) {
        File(source).renameTo(File(destination))
    }
}
