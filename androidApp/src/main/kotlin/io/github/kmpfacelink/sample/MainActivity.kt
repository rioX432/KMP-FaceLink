package io.github.kmpfacelink.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.PlatformContext

internal enum class TrackingMode {
    HOME, FACE, HAND, HOLISTIC, ACTIONS, VOICE, INTERACT
}

class MainActivity : ComponentActivity() {

    private val platformContext by lazy { PlatformContext(this, this) }

    private val stateHolder by lazy {
        TrackingStateHolder(platformContext, lifecycleScope)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            stateHolder.startTracking()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val mode by stateHolder.trackingModeState.collectAsState()
            MainScreen(
                mode = mode,
                stateHolder = stateHolder,
                onModeChange = { newMode -> stateHolder.switchMode(newMode) },
                onStartClick = { requestCameraAndStart() },
                onStopClick = { stateHolder.stopTracking() },
            )
        }
    }

    private fun requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            stateHolder.startTracking()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStop() {
        super.onStop()
        stateHolder.stopIfTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateHolder.release()
    }
}
