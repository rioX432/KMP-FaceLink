package io.github.kmpfacelink.capture

import androidx.camera.core.Preview
import org.junit.Test
import kotlin.test.assertEquals

class PreviewSurfaceBindingTest {
    @Test
    fun attachAndDetachOnlyChangeThePreviewSurfaceProvider() {
        val binding = PreviewSurfaceBinding()
        val provider = Preview.SurfaceProvider { }
        val updates = mutableListOf<Preview.SurfaceProvider?>()

        binding.attach(provider, updates::add)
        binding.detach(provider, updates::add)

        assertEquals(listOf(provider, null), updates)
        assertEquals(null, binding.current)
    }
}
