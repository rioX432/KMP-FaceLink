package io.github.kmpfacelink.capture

/**
 * Applies a retake-delete continuation only while its guided run remains non-terminal.
 *
 * The storage call may already be in flight when a frame-source failure terminates the run. This
 * boundary cannot cancel platform I/O, but it prevents that delayed completion from mutating UI
 * selection, saved state, workflow stage, or participant messaging after terminal.
 */
internal suspend fun runTerminalAwareRetakeDelete(
    isTerminal: () -> Boolean,
    delete: suspend () -> Unit,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
): Boolean {
    if (isTerminal()) return false
    val result = runCatching { delete() }
    if (isTerminal()) return false
    result.fold(
        onSuccess = { onSuccess() },
        onFailure = { onFailure() },
    )
    return true
}
