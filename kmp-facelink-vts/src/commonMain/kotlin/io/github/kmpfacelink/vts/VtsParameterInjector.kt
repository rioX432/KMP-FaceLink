package io.github.kmpfacelink.vts

/**
 * Handles VTube Studio parameter injection and custom parameter creation.
 *
 * @param transport Transport layer for sending requests and receiving responses
 */
internal class VtsParameterInjector(
    private val transport: VtsTransport,
) {
    /**
     * Injects parameter values into VTube Studio.
     *
     * Must be called at least once per second per parameter to prevent
     * VTS from reverting to its own tracking values.
     *
     * @param parameters List of parameter values to inject
     * @param faceFound Whether face tracking is active
     * @param mode Injection mode: "set" (exclusive) or "add" (shared)
     * @throws VtsApiException if VTS returns an API error
     */
    suspend fun injectParameters(
        parameters: List<ParameterValue>,
        faceFound: Boolean = true,
        mode: String = "set",
    ) {
        val request = VtsMessageBuilder.buildInjectRequest(
            parameters = parameters,
            faceFound = faceFound,
            mode = mode,
            requestId = transport.nextRequestId(),
        )
        transport.sendAndReceive(request)
    }

    /**
     * Creates a custom parameter in VTube Studio.
     *
     * Custom parameters appear in the VTS parameter list with a blue "P" indicator.
     * Each plugin can create up to 100 custom parameters (300 total across all plugins).
     *
     * @param name Parameter name (4-32 alphanumeric characters, no spaces)
     * @param explanation Optional description (max 256 characters)
     * @param min Minimum value (default 0)
     * @param max Maximum value (default 1)
     * @param defaultValue Default value (default 0)
     * @throws VtsApiException if VTS returns an API error
     */
    suspend fun createParameter(
        name: String,
        explanation: String = "",
        min: Float = 0f,
        max: Float = 1f,
        defaultValue: Float = 0f,
    ) {
        val data = ParameterCreationData(
            parameterName = name,
            explanation = explanation,
            min = min,
            max = max,
            defaultValue = defaultValue,
        )
        val request = VtsMessageBuilder.buildParameterCreationRequest(
            data = data,
            requestId = transport.nextRequestId(),
        )
        transport.sendAndReceive(request)
    }
}
