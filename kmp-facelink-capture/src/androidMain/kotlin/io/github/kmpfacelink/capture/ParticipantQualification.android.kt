package io.github.kmpfacelink.capture

internal actual fun participantQualificationController(
    session: CaptureSession,
): ParticipantQualificationController? =
    (session as? AndroidCaptureSession)?.participantQualification

