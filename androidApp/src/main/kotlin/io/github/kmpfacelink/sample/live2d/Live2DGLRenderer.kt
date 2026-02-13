package io.github.kmpfacelink.sample.live2d

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.live2d.sdk.cubism.core.ICubismLogger
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel
import io.github.kmpfacelink.live2d.Live2DModelInfo
import io.github.kmpfacelink.live2d.Live2DRenderState
import io.github.kmpfacelink.live2d.Live2DRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Android implementation of [Live2DRenderer] using GLSurfaceView and CubismJavaFramework.
 *
 * This class is both a [GLSurfaceView.Renderer] (for the GL draw cycle) and a
 * [Live2DRenderer] (for the kmp-facelink-live2d pipeline).
 */
internal class Live2DGLRenderer(
    private val assetManager: AssetManager,
) : GLSurfaceView.Renderer, Live2DRenderer {

    private val _state = MutableStateFlow(Live2DRenderState.UNINITIALIZED)
    override val state: StateFlow<Live2DRenderState> = _state.asStateFlow()

    private var _modelInfo: Live2DModelInfo? = null
    override val modelInfo: Live2DModelInfo? get() = _modelInfo

    private var modelManager: Live2DModelManager? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var pendingModelInfo: Live2DModelInfo? = null
    private val pendingParameters = AtomicReference<Map<String, Float>>(emptyMap())
    private var lastFrameTimeMs = 0L

    // GL lifecycle
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        initCubismFramework()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        lastFrameTimeMs = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)

        // Load model if pending
        val info = pendingModelInfo
        if (info != null) {
            pendingModelInfo = null
            loadModelOnGLThread(info)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.currentTimeMillis()
        val deltaTimeSec = (now - lastFrameTimeMs).coerceAtLeast(1) / MS_PER_SEC
        lastFrameTimeMs = now

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val manager = modelManager ?: return

        // Apply pending parameters
        val params = pendingParameters.getAndSet(emptyMap())
        for ((id, value) in params) {
            manager.setParameter(id, value)
        }

        manager.updateModel(deltaTimeSec)

        // Build projection matrix
        val projection = buildProjectionMatrix()
        manager.drawModel(projection)

        if (_state.value == Live2DRenderState.READY) {
            _state.value = Live2DRenderState.RENDERING
        }
    }

    // Live2DRenderer interface
    override suspend fun initialize(modelInfo: Live2DModelInfo) {
        check(_state.value == Live2DRenderState.UNINITIALIZED) {
            "Renderer already initialized (state=${_state.value})"
        }
        _modelInfo = modelInfo
        pendingModelInfo = modelInfo
    }

    override fun updateParameters(parameters: Map<String, Float>) {
        pendingParameters.set(parameters)
    }

    override fun release() {
        modelManager?.releaseModel()
        modelManager = null
        _state.value = Live2DRenderState.RELEASED
    }

    private fun initCubismFramework() {
        if (CubismFramework.isInitialized()) return

        CubismFramework.cleanUp()
        val option = CubismFramework.Option()
        option.logFunction = ICubismLogger { message -> Log.d(TAG, message) }
        option.loggingLevel = LogLevel.WARNING
        CubismFramework.startUp(option)
        CubismFramework.initialize()
    }

    private fun loadModelOnGLThread(info: Live2DModelInfo) {
        try {
            val dir = info.modelPath.substringBeforeLast('/') + "/"
            val fileName = info.modelPath.substringAfterLast('/')
            val manager = Live2DModelManager(assetManager)
            manager.loadModel(dir, fileName)
            modelManager = manager
            _state.value = Live2DRenderState.READY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Live2D model", e)
            _state.value = Live2DRenderState.ERROR
        }
    }

    private fun buildProjectionMatrix(): FloatArray {
        val matrix = com.live2d.sdk.cubism.framework.math.CubismMatrix44.create()
        matrix.loadIdentity()
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            val aspect = surfaceWidth.toFloat() / surfaceHeight
            if (aspect > 1f) {
                matrix.scale(1f / aspect, 1f)
            } else {
                matrix.scale(1f, aspect)
            }
        }
        return matrix.array
    }

    companion object {
        private const val TAG = "Live2DGLRenderer"
        private const val MS_PER_SEC = 1000f
    }
}
