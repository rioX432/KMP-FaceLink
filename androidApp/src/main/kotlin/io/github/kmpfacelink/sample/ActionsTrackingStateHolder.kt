package io.github.kmpfacelink.sample

import io.github.kmpfacelink.actions.ActionBinding
import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.actions.emotion.EmotionClassifier
import io.github.kmpfacelink.actions.emotion.EmotionResult
import io.github.kmpfacelink.actions.record.TrackingRecorder
import io.github.kmpfacelink.actions.record.TrackingSession
import io.github.kmpfacelink.actions.smileTrigger
import io.github.kmpfacelink.actions.surprisedTrigger
import io.github.kmpfacelink.actions.tongueOutTrigger
import io.github.kmpfacelink.actions.winkLeftTrigger
import io.github.kmpfacelink.actions.winkRightTrigger
import io.github.kmpfacelink.api.FaceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val TRIGGER_NAMES = listOf("smile", "winkL", "winkR", "tongueOut", "surprised")

/**
 * Holds action system, emotion classifier, recorder, and coroutine jobs for ActionsTrackingScreen.
 */
internal class ActionsTrackingStateHolder(private val scope: CoroutineScope) {
    val actionSystem = ActionSystem()
    val classifier = EmotionClassifier()
    val recorder = TrackingRecorder()
    var actionsJob: Job? = null
    var eventsJob: Job? = null

    fun startTracking(
        faceTracker: FaceTracker,
        activeTriggers: MutableList<String>,
        onEmotionResult: (EmotionResult?) -> Unit,
    ) {
        eventsJob = scope.launch {
            actionSystem.events.collect { event ->
                handleActionEvent(event, activeTriggers)
            }
        }
        actionsJob = scope.launch {
            faceTracker.trackingData.collect { data ->
                onEmotionResult(classifier.classify(data))
                actionSystem.processFace(data)
                if (recorder.isRecording) recorder.record(data)
            }
        }
    }

    fun stopTracking() {
        actionsJob?.cancel()
        eventsJob?.cancel()
    }

    suspend fun toggleRecording(
        isRecording: Boolean,
        onRecordingChange: (Boolean) -> Unit,
        onSessionChange: (TrackingSession?) -> Unit,
    ) {
        if (isRecording) {
            onSessionChange(recorder.stop())
            onRecordingChange(false)
        } else {
            recorder.start()
            onRecordingChange(true)
            onSessionChange(null)
        }
    }

    suspend fun registerTriggers() {
        actionSystem.register(ActionBinding("smile", smileTrigger()))
        actionSystem.register(ActionBinding("winkL", winkLeftTrigger()))
        actionSystem.register(ActionBinding("winkR", winkRightTrigger()))
        actionSystem.register(ActionBinding("tongueOut", tongueOutTrigger()))
        actionSystem.register(ActionBinding("surprised", surprisedTrigger()))
    }

    fun release() {
        actionsJob?.cancel()
        eventsJob?.cancel()
        actionSystem.release()
    }
}

internal fun handleActionEvent(event: ActionEvent, activeTriggers: MutableList<String>) {
    when (event) {
        is ActionEvent.Started -> {
            if (event.actionId !in activeTriggers) activeTriggers.add(event.actionId)
        }
        is ActionEvent.Released -> activeTriggers.remove(event.actionId)
        is ActionEvent.Held -> { /* Keep active */ }
    }
}
