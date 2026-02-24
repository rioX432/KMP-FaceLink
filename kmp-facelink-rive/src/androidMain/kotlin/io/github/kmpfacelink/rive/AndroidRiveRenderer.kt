package io.github.kmpfacelink.rive

import android.content.Context
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Android implementation of [RiveRenderer] wrapping a [RiveAnimationView].
 *
 * All input operations are thread-safe — Rive's Android runtime queues state machine
 * inputs and processes them on its worker thread during the next `advance()` call.
 *
 * @param context Android context for resource loading
 * @param riveView the [RiveAnimationView] to drive
 */
public class AndroidRiveRenderer(
    private val context: Context,
    private val riveView: RiveAnimationView,
) : RiveRenderer {

    private val _state = MutableStateFlow(RiveRenderState.UNINITIALIZED)
    override val state: StateFlow<RiveRenderState> = _state.asStateFlow()

    private var stateMachineName: String = ""

    override suspend fun loadModel(assetPath: String, stateMachineName: String) {
        _state.value = RiveRenderState.LOADING
        this.stateMachineName = stateMachineName
        try {
            withContext(Dispatchers.Main) {
                riveView.setRiveResource(
                    resId = resolveRawResource(assetPath),
                    stateMachineName = stateMachineName,
                    autoplay = true,
                )
            }
            _state.value = RiveRenderState.READY
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            _state.value = RiveRenderState.ERROR
            throw e
        }
    }

    override fun setNumberInput(name: String, value: Float) {
        if (_state.value != RiveRenderState.READY) return
        riveView.setNumberState(stateMachineName, name, value)
    }

    override fun setBooleanInput(name: String, value: Boolean) {
        if (_state.value != RiveRenderState.READY) return
        riveView.setBooleanState(stateMachineName, name, value)
    }

    override fun fireTrigger(name: String) {
        if (_state.value != RiveRenderState.READY) return
        riveView.fireState(stateMachineName, name)
    }

    override fun updateInputs(inputs: Map<String, RiveInput>) {
        if (_state.value != RiveRenderState.READY) return
        for ((name, input) in inputs) {
            when (input) {
                is RiveInput.Number -> riveView.setNumberState(stateMachineName, name, input.value)
                is RiveInput.BooleanInput -> riveView.setBooleanState(stateMachineName, name, input.value)
                is RiveInput.Trigger -> riveView.fireState(stateMachineName, name)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun release() {
        try {
            riveView.pause()
        } catch (_: Exception) {
            // Best-effort cleanup — Rive may already be in an invalid state
        }
        _state.value = RiveRenderState.RELEASED
    }

    private fun resolveRawResource(assetPath: String): Int {
        val resourceName = assetPath
            .substringAfterLast("/")
            .substringBeforeLast(".")
        return context.resources.getIdentifier(resourceName, "raw", context.packageName)
    }
}
