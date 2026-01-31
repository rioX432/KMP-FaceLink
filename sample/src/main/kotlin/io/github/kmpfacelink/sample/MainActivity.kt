package io.github.kmpfacelink.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val faceTracker by lazy {
        createFaceTracker(
            platformContext = PlatformContext(this, this),
            config = FaceTrackerConfig(
                enableSmoothing = true,
                smoothingFactor = 0.4f,
            ),
        )
    }
    private var isTracking = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startTracking()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                requestCameraAndStart()
            }
        }

        observeState()
        observeTrackingData()
    }

    private fun requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startTracking()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startTracking() {
        lifecycleScope.launch {
            faceTracker.start()
            isTracking = true
            binding.btnToggle.text = "Stop Tracking"
        }
    }

    private fun stopTracking() {
        lifecycleScope.launch {
            faceTracker.stop()
            isTracking = false
            binding.btnToggle.text = "Start Tracking"
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            faceTracker.state.collect { state ->
                binding.tvStatus.text = "Status: $state"
            }
        }
    }

    private fun observeTrackingData() {
        lifecycleScope.launch {
            faceTracker.trackingData.collect { data ->
                if (!data.isTracking) {
                    binding.tvHeadRotation.text = "No face detected"
                    binding.tvBlendShapes.text = ""
                    return@collect
                }

                val ht = data.headTransform
                binding.tvHeadRotation.text = String.format(
                    "Head: P=%.1f Y=%.1f R=%.1f",
                    ht.pitch, ht.yaw, ht.roll,
                )

                val sb = StringBuilder()
                for ((shape, value) in data.blendShapes.entries.sortedBy { it.key.name }) {
                    val bar = "█".repeat((value * 20).toInt()).padEnd(20, '░')
                    sb.appendLine("${shape.arKitName.padEnd(25)} $bar %.2f".format(value))
                }
                binding.tvBlendShapes.text = sb.toString()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceTracker.release()
    }
}
