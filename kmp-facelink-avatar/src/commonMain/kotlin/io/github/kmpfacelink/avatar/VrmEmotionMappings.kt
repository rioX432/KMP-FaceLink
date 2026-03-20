package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.valueOf

/**
 * VRM emotion expression mappings derived from ARKit blend shapes.
 *
 * Derives emotion intensities (Joy, Angry, Sorrow, Surprise, Neutral, Relaxed/Fun)
 * from combinations of brow, mouth, eye, and cheek blend shapes.
 */
internal object VrmEmotionMappings {

    private const val HALF = 0.5f
    private const val SMILE_WEIGHT = 0.4f
    private const val CHEEK_SQUINT_WEIGHT = 0.2f
    private const val BROW_DOWN_WEIGHT = 0.3f
    private const val FROWN_WEIGHT = 0.3f
    private const val BROW_INNER_UP_WEIGHT = 0.4f
    private const val WIDE_EYE_WEIGHT = 0.3f
    private const val JAW_OPEN_SURPRISE_WEIGHT = 0.3f

    fun build(version: VrmVersion): Map<String, ParameterMapping> {
        val ids = VrmIdResolver.emotionIds(version)
        return mapOf(
            ids.joy to joy(),
            ids.angry to angry(),
            ids.sorrow to sorrow(),
            ids.surprise to surprise(),
            ids.neutral to neutral(),
            ids.relaxed to relaxed(),
        )
    }

    private fun joy(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
        val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
        val cheekL = bs.valueOf(BlendShape.CHEEK_SQUINT_LEFT)
        val cheekR = bs.valueOf(BlendShape.CHEEK_SQUINT_RIGHT)
        val smile = (smileL + smileR) * SMILE_WEIGHT
        val cheek = (cheekL + cheekR) * CHEEK_SQUINT_WEIGHT
        (smile + cheek).coerceIn(0f, 1f)
    }

    private fun angry(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val browL = bs.valueOf(BlendShape.BROW_DOWN_LEFT)
        val browR = bs.valueOf(BlendShape.BROW_DOWN_RIGHT)
        val frownL = bs.valueOf(BlendShape.MOUTH_FROWN_LEFT)
        val frownR = bs.valueOf(BlendShape.MOUTH_FROWN_RIGHT)
        val brow = (browL + browR) * BROW_DOWN_WEIGHT
        val frown = (frownL + frownR) * FROWN_WEIGHT
        (brow + frown).coerceIn(0f, 1f)
    }

    private fun sorrow(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val browUp = bs.valueOf(BlendShape.BROW_INNER_UP) * BROW_INNER_UP_WEIGHT
        val frownL = bs.valueOf(BlendShape.MOUTH_FROWN_LEFT)
        val frownR = bs.valueOf(BlendShape.MOUTH_FROWN_RIGHT)
        val frown = (frownL + frownR) * FROWN_WEIGHT
        (browUp + frown).coerceIn(0f, 1f)
    }

    private fun surprise(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val wideL = bs.valueOf(BlendShape.EYE_WIDE_LEFT)
        val wideR = bs.valueOf(BlendShape.EYE_WIDE_RIGHT)
        val jawOpen = bs.valueOf(BlendShape.JAW_OPEN) * JAW_OPEN_SURPRISE_WEIGHT
        val browUp = bs.valueOf(BlendShape.BROW_INNER_UP) * BROW_INNER_UP_WEIGHT
        val wide = (wideL + wideR) * WIDE_EYE_WEIGHT
        (wide + jawOpen + browUp).coerceIn(0f, 1f)
    }

    private fun neutral(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
        val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
        val smileAvg = (smileL + smileR) * HALF
        val frownL = bs.valueOf(BlendShape.MOUTH_FROWN_LEFT)
        val frownR = bs.valueOf(BlendShape.MOUTH_FROWN_RIGHT)
        val frownAvg = (frownL + frownR) * HALF
        val browL = bs.valueOf(BlendShape.BROW_DOWN_LEFT)
        val browR = bs.valueOf(BlendShape.BROW_DOWN_RIGHT)
        val browDown = (browL + browR) * HALF
        val expressionIntensity = maxOf(smileAvg, frownAvg, browDown)
        (1f - expressionIntensity).coerceIn(0f, 1f)
    }

    private fun relaxed(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
        val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
        val smile = (smileL + smileR) * HALF
        val browL = bs.valueOf(BlendShape.BROW_DOWN_LEFT)
        val browR = bs.valueOf(BlendShape.BROW_DOWN_RIGHT)
        val browDown = (browL + browR) * HALF
        (smile * (1f - browDown)).coerceIn(0f, 1f)
    }
}
