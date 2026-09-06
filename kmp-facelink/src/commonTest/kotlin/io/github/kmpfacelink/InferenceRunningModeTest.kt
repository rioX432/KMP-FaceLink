package io.github.kmpfacelink

import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PerformanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The running mode decides whether MediaPipe re-detects the face on every frame or carries the
 * previous frame's rectangle forward, so two takes measured under different modes are not
 * comparable even when everything else matches. These tests pin the default (which must stay the
 * product's live configuration) and the fact that the other modes are selectable at all.
 */
class InferenceRunningModeTest {

    @Test
    fun defaultsToLiveStreamSoTheProductPathIsUnchanged() {
        assertEquals(InferenceRunningMode.LIVE_STREAM, PerformanceConfig().runningMode)
    }

    @Test
    fun offlineModesAreSelectable() {
        assertEquals(
            InferenceRunningMode.IMAGE,
            PerformanceConfig(runningMode = InferenceRunningMode.IMAGE).runningMode,
        )
        assertEquals(
            InferenceRunningMode.VIDEO,
            PerformanceConfig(runningMode = InferenceRunningMode.VIDEO).runningMode,
        )
    }

    @Test
    fun everyModeIsCovered() {
        // A new entry here has to be mapped in FaceLandmarkerFactory too; the mapping is an
        // exhaustive `when`, so this guards against the enum growing silently.
        assertEquals(3, InferenceRunningMode.entries.size)
    }
}
