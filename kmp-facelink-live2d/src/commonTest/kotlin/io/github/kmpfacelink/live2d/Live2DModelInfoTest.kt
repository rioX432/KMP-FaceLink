package io.github.kmpfacelink.live2d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Live2DModelInfoTest {

    @Test
    fun constructionWithAllFields() {
        val paramIds = setOf("ParamAngleX", "ParamEyeLOpen")
        val info = Live2DModelInfo(
            modelId = "hiyori",
            name = "Hiyori",
            modelPath = "live2d/Hiyori/Hiyori.model3.json",
            parameterIds = paramIds,
        )

        assertEquals("hiyori", info.modelId)
        assertEquals("Hiyori", info.name)
        assertEquals("live2d/Hiyori/Hiyori.model3.json", info.modelPath)
        assertEquals(paramIds, info.parameterIds)
    }

    @Test
    fun parameterIdsDefaultsToEmpty() {
        val info = Live2DModelInfo(
            modelId = "test",
            name = "Test Model",
            modelPath = "models/test.model3.json",
        )

        assertTrue(info.parameterIds.isEmpty())
    }

    @Test
    fun dataClassEquality() {
        val a = Live2DModelInfo("id", "Name", "path.json")
        val b = Live2DModelInfo("id", "Name", "path.json")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun copyPreservesFields() {
        val original = Live2DModelInfo(
            modelId = "original",
            name = "Original",
            modelPath = "original.json",
            parameterIds = setOf("ParamA"),
        )

        val copied = original.copy(name = "Modified")

        assertEquals("original", copied.modelId)
        assertEquals("Modified", copied.name)
        assertEquals("original.json", copied.modelPath)
        assertEquals(setOf("ParamA"), copied.parameterIds)
    }
}
