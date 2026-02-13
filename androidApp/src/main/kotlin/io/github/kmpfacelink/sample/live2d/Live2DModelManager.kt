package io.github.kmpfacelink.sample.live2d

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.ICubismModelSetting
import com.live2d.sdk.cubism.framework.id.CubismId
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid

/**
 * Manages loading and setup of a Live2D Cubism model from Android assets.
 *
 * Wraps [CubismUserModel] to load model data (.model3.json, .moc3, textures)
 * and provides access to parameter manipulation and rendering.
 */
internal class Live2DModelManager(
    private val assetManager: AssetManager,
) : CubismUserModel() {

    private var modelDirectory: String = ""
    private var modelSetting: ICubismModelSetting? = null
    private val parameterIdCache = mutableMapOf<String, CubismId>()

    /**
     * Loads a model from the given assets directory.
     *
     * @param directory asset directory containing the model (e.g. "live2d/Hiyori/")
     * @param modelFileName model settings file name (e.g. "Hiyori.model3.json")
     * @param surfaceWidth current GL surface width
     * @param surfaceHeight current GL surface height
     */
    fun loadModel(
        directory: String,
        modelFileName: String,
    ) {
        modelDirectory = directory

        val settingBytes = loadAssetBytes(directory + modelFileName)
        val setting = CubismModelSettingJson(settingBytes)
        modelSetting = setting

        // Load .moc3
        val mocFileName = setting.modelFileName
        if (!mocFileName.isNullOrEmpty()) {
            val mocBytes = loadAssetBytes(directory + mocFileName)
            loadModel(mocBytes)
        }

        // Load physics
        val physicsFileName = setting.physicsFileName
        if (!physicsFileName.isNullOrEmpty()) {
            val physicsBytes = loadAssetBytes(directory + physicsFileName)
            loadPhysics(physicsBytes)
        }

        // Load pose
        val poseFileName = setting.poseFileName
        if (!poseFileName.isNullOrEmpty()) {
            val poseBytes = loadAssetBytes(directory + poseFileName)
            loadPose(poseBytes)
        }

        // Create renderer
        val renderer = CubismRendererAndroid.create()
        setupRenderer(renderer)

        // Load textures
        loadTextures(setting)
    }

    /** Sets a single parameter value by string ID. Uses cached CubismId lookups. */
    fun setParameter(parameterId: String, value: Float) {
        val model = this.model ?: return
        val cubismId = parameterIdCache.getOrPut(parameterId) {
            CubismFramework.getIdManager().getId(parameterId)
        }
        model.setParameterValue(cubismId, value)
    }

    /** Applies all parameter changes and updates the model state. */
    fun updateModel(deltaTimeSec: Float) {
        val model = this.model ?: return
        physics?.evaluate(model, deltaTimeSec)
        pose?.updateParameters(model, deltaTimeSec)
        model.update()
    }

    /** Draws the model with the given projection matrix. */
    fun drawModel(projectionMatrix: FloatArray) {
        val renderer = getRenderer<CubismRendererAndroid>() ?: return
        val matrix = com.live2d.sdk.cubism.framework.math.CubismMatrix44.create()
        matrix.setMatrix(projectionMatrix)

        com.live2d.sdk.cubism.framework.math.CubismMatrix44.multiply(
            modelMatrix.array,
            matrix.array,
            matrix.array,
        )

        renderer.mvpMatrix = matrix
        renderer.drawModel()
    }

    /** Releases all model resources. */
    fun releaseModel() {
        parameterIdCache.clear()
        modelSetting = null
    }

    private fun loadTextures(setting: ICubismModelSetting) {
        val textureCount = setting.textureCount
        for (i in 0 until textureCount) {
            val texturePath = modelDirectory + setting.getTextureFileName(i)
            val textureId = loadTextureFromAssets(texturePath)
            if (textureId > 0) {
                getRenderer<CubismRendererAndroid>()?.bindTexture(i, textureId)
            }
        }
        getRenderer<CubismRendererAndroid>()?.isPremultipliedAlpha(true)
    }

    private fun loadTextureFromAssets(path: String): Int {
        return try {
            val stream = assetManager.open(path)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()

            textureIds[0]
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load texture: $path", e)
            0
        }
    }

    private fun loadAssetBytes(path: String): ByteArray {
        return assetManager.open(path).use { it.readBytes() }
    }

    companion object {
        private const val TAG = "Live2DModelManager"
    }
}
