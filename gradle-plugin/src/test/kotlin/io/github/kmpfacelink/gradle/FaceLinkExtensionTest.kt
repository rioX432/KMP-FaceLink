package io.github.kmpfacelink.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FaceLinkExtensionTest {

    @Test
    fun `plugin registers faceLink extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val extension = project.extensions.findByName("faceLink")
        assertNotNull(extension, "faceLink extension should be registered")
        assertTrue(extension is FaceLinkExtension)
    }

    @Test
    fun `extension has correct convention defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val ext = project.extensions.getByType(FaceLinkExtension::class.java)

        assertTrue(ext.faceEnabled.get(), "faceEnabled should default to true")
        assertTrue(ext.handEnabled.get(), "handEnabled should default to true")
        assertFalse(ext.bodyEnabled.get(), "bodyEnabled should default to false")
        assertEquals("float16", ext.precision.get())
        assertEquals("src/main/assets/models", ext.outputDir.get())
        assertEquals("latest", ext.mediapipeVersion.get())
    }

    @Test
    fun `extension properties can be overridden`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val ext = project.extensions.getByType(FaceLinkExtension::class.java)
        ext.faceEnabled.set(false)
        ext.bodyEnabled.set(true)
        ext.precision.set("float32")
        ext.outputDir.set("models")
        ext.mediapipeVersion.set("0.10.33")

        assertFalse(ext.faceEnabled.get())
        assertTrue(ext.bodyEnabled.get())
        assertEquals("float32", ext.precision.get())
        assertEquals("models", ext.outputDir.get())
        assertEquals("0.10.33", ext.mediapipeVersion.get())
    }

    @Test
    fun `model registry resolves URLs correctly`() {
        val url = MediaPipeModelRegistry.resolveUrl(
            model = MediaPipeModelRegistry.faceLandmarker,
            precision = "float16",
            version = "latest",
        )
        val expected = "https://storage.googleapis.com/mediapipe-models" +
            "/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
        assertEquals(expected, url)
    }

    @Test
    fun `model registry returns all three models`() {
        val models = MediaPipeModelRegistry.all()
        assertEquals(3, models.size)
        assertEquals("face_landmarker_v2_with_blendshapes.task", models[0].filename)
        assertEquals("hand_landmarker.task", models[1].filename)
        assertEquals("pose_landmarker_lite.task", models[2].filename)
    }

    @Test
    fun `plugin registers downloadMediaPipeModels task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val task = project.tasks.findByName("downloadMediaPipeModels")
        assertNotNull(task, "downloadMediaPipeModels task should be registered")
        assertTrue(task is DownloadMediaPipeModelsTask)
    }

    @Test
    fun `buildModelList respects enabled flags`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val ext = project.extensions.getByType(FaceLinkExtension::class.java)

        // Default: face=true, hand=true, body=false
        val defaultModels = FaceLinkPlugin.buildModelList(ext)
        assertEquals(2, defaultModels.size)
        assertEquals("face_landmarker_v2_with_blendshapes.task", defaultModels[0].filename)
        assertEquals("hand_landmarker.task", defaultModels[1].filename)

        // Enable body, disable face
        ext.faceEnabled.set(false)
        ext.bodyEnabled.set(true)

        val customModels = FaceLinkPlugin.buildModelList(ext)
        assertEquals(2, customModels.size)
        assertEquals("hand_landmarker.task", customModels[0].filename)
        assertEquals("pose_landmarker_lite.task", customModels[1].filename)
    }

    @Test
    fun `writeGitIgnore creates gitignore in output directory`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.rioX432.facelink")

        val ext = project.extensions.getByType(FaceLinkExtension::class.java)
        ext.outputDir.set("build/test-gitignore-models")

        FaceLinkPlugin.writeGitIgnore(project, ext)

        val gitIgnore = File(project.projectDir, "build/test-gitignore-models/.gitignore")
        assertTrue(gitIgnore.exists(), ".gitignore should be created")
        assertTrue(gitIgnore.readText().contains("*.task"), ".gitignore should exclude .task files")
    }
}
