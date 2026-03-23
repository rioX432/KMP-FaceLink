package io.github.kmpfacelink.llm.internal

/**
 * Computes the SHA-256 hex digest of the file at the given path.
 *
 * @param filePath Absolute path to the file
 * @return Lowercase hex-encoded SHA-256 digest
 */
internal expect suspend fun sha256Hex(filePath: String): String
