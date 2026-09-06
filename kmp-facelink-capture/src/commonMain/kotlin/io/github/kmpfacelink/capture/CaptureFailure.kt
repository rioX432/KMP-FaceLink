package io.github.kmpfacelink.capture

/** Stable, public-safe reason why capture could not proceed or publish a take. */
public enum class CaptureFailureCategory(
    public val code: String,
    public val retryable: Boolean,
    public val participantMessageJa: String,
) {
    CAPACITY(
        code = "capacity",
        retryable = false,
        participantMessageJa = "端末の空き容量を増やしてから、もう一度開始してください。",
    ),
    STORAGE_SETUP(
        code = "storage_setup",
        retryable = false,
        participantMessageJa = "保存先を準備できませんでした。アプリを開き直し、再発する場合は撮影を中止してください。",
    ),
    ROOT_IN_USE(
        code = "root_in_use",
        retryable = true,
        participantMessageJa = "別の撮影画面が保存先を使用しています。そちらを閉じてからやり直してください。",
    ),
    STREAM_WRITE(
        code = "stream_write",
        retryable = true,
        participantMessageJa = "撮影データを書き込めませんでした。このテイクを一度だけやり直してください。",
    ),
    INTEGRITY(
        code = "integrity",
        retryable = true,
        participantMessageJa = "保存データを検証できませんでした。このテイクを一度だけやり直してください。",
    ),
    CLEANUP(
        code = "cleanup",
        retryable = false,
        participantMessageJa = "未完了データを安全に片付けられませんでした。撮影を中止してください。",
    ),
    FRAME_SOURCE(
        code = "frame_source",
        retryable = false,
        participantMessageJa = "カメラ入力を受け取れませんでした。撮影画面を開き直し、再発する場合は中止してください。",
    ),
    CONFIGURATION(
        code = "configuration",
        retryable = false,
        participantMessageJa = "この撮影設定は利用できません。担当者へ確認してください。",
    ),
    UNKNOWN(
        code = "unknown",
        retryable = false,
        participantMessageJa = "原因を特定できないエラーが発生しました。撮影を中止してください。",
    ),
    ;

    public fun receipt(): CaptureFailureReceipt = CaptureFailureReceipt(
        code = code,
        retryable = retryable,
        participantMessageJa = participantMessageJa,
    )
}

/** Contains no raw exception, path, device identity, or capture payload. */
public data class CaptureFailureReceipt(
    val code: String,
    val retryable: Boolean,
    val participantMessageJa: String,
)

/** The only two workflow actions a participant-facing capture failure may expose. */
internal enum class CaptureFailurePrimaryAction {
    RETRY_TAKE,
    STOP_RUN,
}

/** Public-safe failure copy used by the shared Android/iOS workflow. */
internal data class CaptureFailurePresentation(
    val participantMessageJa: String,
    val primaryAction: CaptureFailurePrimaryAction,
)

internal fun CaptureFailureReceipt.toPresentation(): CaptureFailurePresentation =
    CaptureFailurePresentation(
        participantMessageJa = participantMessageJa,
        primaryAction = if (retryable) {
            CaptureFailurePrimaryAction.RETRY_TAKE
        } else {
            CaptureFailurePrimaryAction.STOP_RUN
        },
    )

internal fun cancelledCaptureFailurePresentation(): CaptureFailurePresentation =
    CaptureFailurePresentation(
        participantMessageJa = "撮影を中止しました。データは保存されていません。",
        primaryAction = CaptureFailurePrimaryAction.RETRY_TAKE,
    )

/** Typed internal cause whose public message is deliberately only the stable code. */
public class CaptureFailureException(
    public val category: CaptureFailureCategory,
    cause: Throwable? = null,
) : IllegalStateException(category.code, cause)

public fun captureFailure(
    category: CaptureFailureCategory,
    cause: Throwable? = null,
): CaptureFailureException = CaptureFailureException(category, cause)

/**
 * Converts a throwable to a public-safe receipt without inspecting or copying its message.
 *
 * Cause-chain typing survives platform wrappers. Suppressed failures never override the primary
 * category: a cleanup error attached to a stream-write error remains diagnostic detail, not the
 * participant's primary action.
 */
public fun Throwable.toCaptureFailureReceipt(
    defaultCategory: CaptureFailureCategory = CaptureFailureCategory.UNKNOWN,
): CaptureFailureReceipt {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is CaptureFailureException) return current.category.receipt()
        current = current.cause
    }
    return defaultCategory.receipt()
}
