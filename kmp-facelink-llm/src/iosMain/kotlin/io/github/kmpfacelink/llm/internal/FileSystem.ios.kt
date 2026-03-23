@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.kmpfacelink.llm.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

internal actual object FileSystem {
    private val fileManager = NSFileManager.defaultManager

    actual fun fileSize(path: String): Long {
        val attributes = fileManager.attributesOfItemAtPath(path, null) ?: return -1L
        val size = attributes["NSFileSize"]
        return (size as? Number)?.toLong() ?: -1L
    }

    actual fun exists(path: String): Boolean = fileManager.fileExistsAtPath(path)

    actual fun delete(path: String): Boolean = fileManager.removeItemAtPath(path, null)

    actual fun mkdirs(path: String) {
        fileManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    actual fun listFiles(directory: String): List<String> {
        return fileManager.contentsOfDirectoryAtPath(directory, null)
            ?.filterIsInstance<String>()
            ?: emptyList()
    }

    actual fun readText(path: String): String? {
        if (!exists(path)) return null
        return NSString.stringWithContentsOfFile(path) as? String
    }

    actual fun writeText(path: String, content: String) {
        val nsString = NSString.create(string = content)
        nsString.writeToFile(path, atomically = true)
    }

    actual fun rename(source: String, destination: String) {
        if (exists(destination)) {
            delete(destination)
        }
        fileManager.moveItemAtPath(source, toPath = destination, error = null)
    }
}
