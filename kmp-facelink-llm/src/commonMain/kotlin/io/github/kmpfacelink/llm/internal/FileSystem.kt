package io.github.kmpfacelink.llm.internal

/**
 * Platform-agnostic file system operations needed by [DefaultModelManager].
 */
internal expect object FileSystem {
    /** Returns the size of the file in bytes, or -1 if it does not exist. */
    fun fileSize(path: String): Long

    /** Returns true if the file exists. */
    fun exists(path: String): Boolean

    /** Deletes the file at the given path. Returns true if deleted. */
    fun delete(path: String): Boolean

    /** Creates directories along the path if they do not exist. */
    fun mkdirs(path: String)

    /** Lists file names in the given directory. */
    fun listFiles(directory: String): List<String>

    /** Reads the entire text content of the file. Returns null if not found. */
    fun readText(path: String): String?

    /** Writes the text content to the file, overwriting if it exists. */
    fun writeText(path: String, content: String)

    /** Renames/moves a file from [source] to [destination]. */
    fun rename(source: String, destination: String)
}
