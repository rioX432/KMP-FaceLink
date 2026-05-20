package io.github.kmpfacelink.sample

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.api.PlatformContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [PlatformContext] creation and property access on Android.
 */
@RunWith(AndroidJUnit4::class)
class PlatformContextTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private fun createTestLifecycleOwner(): LifecycleOwner {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        return owner
    }

    @Test
    fun createWithApplicationContext() {
        val lifecycleOwner = createTestLifecycleOwner()
        val platformContext = PlatformContext(appContext, lifecycleOwner)
        assertNotNull("PlatformContext should be created", platformContext)
    }

    @Test
    fun contextPropertyReturnsProvidedContext() {
        val lifecycleOwner = createTestLifecycleOwner()
        val platformContext = PlatformContext(appContext, lifecycleOwner)
        assertEquals(
            "context property should return the provided context",
            appContext,
            platformContext.context,
        )
    }

    @Test
    fun lifecycleOwnerPropertyReturnsProvidedOwner() {
        val lifecycleOwner = createTestLifecycleOwner()
        val platformContext = PlatformContext(appContext, lifecycleOwner)
        assertEquals(
            "lifecycleOwner should return the provided owner",
            lifecycleOwner,
            platformContext.lifecycleOwner,
        )
    }

    @Test
    fun createWithApplicationClassContext() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val lifecycleOwner = createTestLifecycleOwner()
        val platformContext = PlatformContext(application, lifecycleOwner)
        assertNotNull("PlatformContext should accept Application as context", platformContext)
        assertEquals(application, platformContext.context)
    }

    @Test
    fun twoInstancesWithSameContextAreIndependent() {
        val lifecycleOwner1 = createTestLifecycleOwner()
        val lifecycleOwner2 = createTestLifecycleOwner()
        val ctx1 = PlatformContext(appContext, lifecycleOwner1)
        val ctx2 = PlatformContext(appContext, lifecycleOwner2)

        assertEquals("Both should share the same context", ctx1.context, ctx2.context)
        assertEquals(
            "First should use lifecycleOwner1",
            lifecycleOwner1,
            ctx1.lifecycleOwner,
        )
        assertEquals(
            "Second should use lifecycleOwner2",
            lifecycleOwner2,
            ctx2.lifecycleOwner,
        )
    }
}
