package com.undercarriage.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.undercarriage.scanner.ai.CoverageAnalyzer
import com.undercarriage.scanner.audio.SonicFeedbackManager
import com.undercarriage.scanner.camera.CameraManager
import com.undercarriage.scanner.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
        )
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var sonicFeedbackManager: SonicFeedbackManager
    private lateinit var coverageAnalyzer: CoverageAnalyzer
    
    private var isScanning = false
    private var capturedImages = mutableListOf<String>()
    
    // OpenCV initialization callback
    private val openCVLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.d(TAG, "OpenCV loaded successfully")
                    initializeComponents()
                }
                else -> {
                    super.onManagerConnected(status)
                    Log.e(TAG, "OpenCV initialization failed")
                    showError("OpenCV initialization failed")
                }
            }
        }
    }
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeOpenCV()
        } else {
            showError("Camera permission is required for this app")
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkAndRequestPermissions()
    }
    
    private fun setupUI() {
        binding.apply {
            // Start/Stop scanning button
            btnStartStop.setOnClickListener {
                if (isScanning) {
                    stopScanning()
                } else {
                    startScanning()
                }
            }
            
            // Capture button (manual capture)
            btnCapture.setOnClickListener {
                captureImage()
            }
            
            // Flashlight toggle
            btnFlashlight.setOnClickListener {
                cameraManager.toggleFlashlight()
            }
            
            // Reset coverage
            btnReset.setOnClickListener {
                resetCoverage()
            }
            
            // View results
            btnViewResults.setOnClickListener {
                viewResults()
            }
        }
        
        updateUI()
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            initializeOpenCV()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun initializeOpenCV() {
        // OpenCV 4.9.0+ uses initLocal() for Maven Central distribution
        if (OpenCVLoader.initLocal()) {
            Log.d(TAG, "OpenCV loaded successfully")
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            Log.d(TAG, "OpenCV initialization failed, trying async loading")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback)
        }
    }
    
    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                // Initialize camera manager
                cameraManager = CameraManager(this@MainActivity)
                val cameraInitialized = cameraManager.initializeCamera()
                
                if (!cameraInitialized) {
                    showError("Failed to initialize camera")
                    return@launch
                }
                
                // Initialize audio feedback
                sonicFeedbackManager = SonicFeedbackManager(this@MainActivity)
                sonicFeedbackManager.initialize()
                
                // Initialize coverage analyzer
                coverageAnalyzer = CoverageAnalyzer()
                
                // Start camera preview
                startCameraPreview()
                
                Log.d(TAG, "All components initialized successfully")
                
            } catch (exception: Exception) {
                Log.e(TAG, "Error initializing components", exception)
                showError("Initialization failed: ${exception.message}")
            }
        }
    }
    
    private fun startCameraPreview() {
        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = binding.previewView,
            callback = object : CameraManager.CameraCallback {
                override fun onImageCaptured(imageProxy: ImageProxy) {
                    // Handle captured image
                    Log.d(TAG, "Image captured")
                    imageProxy.close()
                }
                
                override fun onAnalysisFrame(imageProxy: ImageProxy) {
                    if (isScanning) {
                        processAnalysisFrame(imageProxy)
                    }
                }
                
                override fun onError(exception: Exception) {
                    Log.e(TAG, "Camera error", exception)
                    showError("Camera error: ${exception.message}")
                }
            }
        )
    }
    
    private fun processAnalysisFrame(imageProxy: ImageProxy) {
        try {
            val analysisResult = coverageAnalyzer.analyzeFrame(imageProxy)
            
            // Update UI with coverage info
            runOnUiThread {
                updateCoverageUI(analysisResult)
            }
            
            // Provide audio guidance
            val direction = SonicFeedbackManager.GuidanceDirection(
                deltaX = analysisResult.deltaX / 100f, // Normalize
                deltaY = analysisResult.deltaY / 100f,
                distance = 1f - analysisResult.coverage,
                isStable = analysisResult.isStable
            )
            
            sonicFeedbackManager.provideDirectionalGuidance(direction)
            
            // Auto-capture if conditions are met
            if (analysisResult.shouldCapture) {
                runOnUiThread {
                    captureImage()
                }
            }
            
            // Check if scanning is complete
            if (coverageAnalyzer.isComplete()) {
                runOnUiThread {
                    completeScan()
                }
            }
            
        } catch (exception: Exception) {
            Log.e(TAG, "Error processing analysis frame", exception)
        }
    }
    
    private fun updateCoverageUI(result: CoverageAnalyzer.AnalysisResult) {
        binding.apply {
            // Update coverage progress
            progressCoverage.progress = (result.coverage * 100).toInt()
            tvCoveragePercent.text = "${(result.coverage * 100).toInt()}%"
            
            // Update movement indicators
            tvMovementX.text = "X: ${String.format("%.1f", result.deltaX)}"
            tvMovementY.text = "Y: ${String.format("%.1f", result.deltaY)}"
            tvRotation.text = "Rot: ${String.format("%.1f", result.rotation)}°"
            
            // Update stability indicator
            indicatorStability.setBackgroundColor(
                if (result.isStable) {
                    ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light)
                } else {
                    ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light)
                }
            )
            
            // Update captured images count
            tvCapturedCount.text = "Captured: ${capturedImages.size}"
        }
    }
    
    private fun startScanning() {
        isScanning = true
        coverageAnalyzer.reset()
        capturedImages.clear()
        updateUI()
        
        Toast.makeText(this, "Scanning started", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Scanning started")
    }
    
    private fun stopScanning() {
        isScanning = false
        updateUI()
        
        Toast.makeText(this, "Scanning stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Scanning stopped")
    }
    
    private fun captureImage() {
        if (!::cameraManager.isInitialized) return
        
        cameraManager.captureImage(object : CameraManager.CameraCallback {
            override fun onImageCaptured(imageProxy: ImageProxy) {
                // Add to captured images list
                capturedImages.add("image_${System.currentTimeMillis()}.jpg")
                
                runOnUiThread {
                    updateCoverageUI(CoverageAnalyzer.AnalysisResult(0f, 0f, 0f, 0f, false, false))
                    Toast.makeText(this@MainActivity, "Image captured", Toast.LENGTH_SHORT).show()
                }
                
                imageProxy.close()
            }
            
            override fun onAnalysisFrame(imageProxy: ImageProxy) {
                // Not used for manual capture
            }
            
            override fun onError(exception: Exception) {
                runOnUiThread {
                    showError("Capture failed: ${exception.message}")
                }
            }
        })
    }
    
    private fun resetCoverage() {
        coverageAnalyzer.reset()
        capturedImages.clear()
        
        runOnUiThread {
            binding.progressCoverage.progress = 0
            binding.tvCoveragePercent.text = "0%"
            binding.tvCapturedCount.text = "Captured: 0"
        }
        
        Toast.makeText(this, "Coverage reset", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Coverage reset")
    }
    
    private fun completeScan() {
        stopScanning()
        sonicFeedbackManager.playCompletionFeedback()
        
        Toast.makeText(this, "Scan completed! Coverage target reached.", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Scan completed")
    }
    
    private fun viewResults() {
        val heatmap = coverageAnalyzer.getCoverageHeatmap()
        if (heatmap != null) {
            // TODO: Show results activity with heatmap and captured images
            Toast.makeText(this, "Results: ${capturedImages.size} images captured", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No results available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateUI() {
        binding.apply {
            btnStartStop.text = if (isScanning) "Stop Scanning" else "Start Scanning"
            btnCapture.isEnabled = !isScanning // Disable manual capture during auto-scanning
            
            // Enable/disable other controls based on scanning state
            btnReset.isEnabled = !isScanning
            btnViewResults.isEnabled = capturedImages.isNotEmpty()
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        Log.e(TAG, message)
    }
    
    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initLocal()) {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback)
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::sonicFeedbackManager.isInitialized) {
            // Pause audio feedback
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
        
        if (::sonicFeedbackManager.isInitialized) {
            sonicFeedbackManager.shutdown()
        }
        
        if (::coverageAnalyzer.isInitialized) {
            coverageAnalyzer.reset()
        }
    }
}