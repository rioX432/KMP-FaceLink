package io.github.kmpfacelink.rive

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.api.Releasable
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for rendering Rive animations driven by face tracking.
 *
 * Implementations wrap platform-specific Rive SDKs:
 * - Android: `AndroidRiveRenderer` wraps `RiveAnimationView`
 * - iOS: Implemented in Swift using `RiveViewModel` (rive-ios is Swift-only)
 */
@ExperimentalFaceLinkApi
public interface RiveRenderer : Releasable {

    /** Current lifecycle state. */
    public val state: StateFlow<RiveRenderState>

    /**
     * Loads a Rive model and prepares the state machine.
     *
     * @param assetPath path to the .riv asset file
     * @param stateMachineName name of the state machine to use
     */
    public suspend fun loadModel(assetPath: String, stateMachineName: String)

    /**
     * Sets a numeric input on the active state machine.
     *
     * @param name the input name
     * @param value the float value to set
     */
    public fun setNumberInput(name: String, value: Float)

    /**
     * Sets a boolean input on the active state machine.
     *
     * @param name the input name
     * @param value the boolean value to set
     */
    public fun setBooleanInput(name: String, value: Boolean)

    /**
     * Fires a trigger input on the active state machine.
     *
     * @param name the trigger name
     */
    public fun fireTrigger(name: String)

    /**
     * Batch-applies all inputs from the given map.
     *
     * @param inputs map of input name to [RiveInput] values
     */
    public fun updateInputs(inputs: Map<String, RiveInput>)

    /** Releases all resources. The renderer cannot be reused after this call. */
    public override fun release()
}
