package io.github.kmpfacelink.vts

/**
 * Maps internal VTS API response data to public [PostProcessingState] model.
 */
internal fun mapPostProcessingState(
    data: PostProcessingListResponseData,
): PostProcessingState = PostProcessingState(
    isSupported = data.postProcessingSupported,
    isActive = data.postProcessingActive,
    activePreset = data.activePreset,
    effects = data.postProcessingEffects.map { effect ->
        PostProcessingEffect(
            id = effect.internalID,
            enumId = effect.enumID,
            explanation = effect.explanation,
            isActive = effect.isActive,
            configEntries = effect.configEntries.map { entry ->
                PostProcessingConfigEntry(
                    id = entry.internalID,
                    explanation = entry.explanation,
                    type = entry.type,
                    currentValue = entry.currentValue,
                    minValue = entry.minValue,
                    maxValue = entry.maxValue,
                    defaultValue = entry.defaultValue,
                )
            },
        )
    },
)
