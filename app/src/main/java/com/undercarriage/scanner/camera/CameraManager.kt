package com.undercarriage.scanner.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraManager"
        private const val PREFERRED_RESOLUTION_WIDTH = 1920
        private const val PREFERRED_RESOLUTION_HEIGHT = 1080
    }
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    interface CameraCallback {
        fun onImageCaptured(imageProxy: ImageProxy)
        fun onAnalysisFrame(imageProxy: ImageProxy)
        fun onError(exception: Exception)
    }
    
    suspend fun initializeCamera(): Boolean = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                continuation.resume(true)
            } catch (exception: Exception) {
                Log.e(TAG, "Camera initialization failed", exception)
                continuation.resume(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        callback: CameraCallback
    ) {
        val cameraProvider = this.cameraProvider ?: run {
            callback.onError(IllegalStateException("Camera not initialized"))
            return
        }
        
        try {
            // Configure preview
            preview = Preview.Builder()
                .setTargetResolution(Size(PREFERRED_RESOLUTION_WIDTH, PREFERRED_RESOLUTION_HEIGHT))
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Configure image capture with low-light optimizations
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(PREFERRED_RESOLUTION_WIDTH, PREFERRED_RESOLUTION_HEIGHT))
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                .build()
            
            // Configure image analysis for real-time processing
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480)) // Lower resolution for analysis
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            callback.onAnalysisFrame(imageProxy)
                        } catch (exception: Exception) {
                            Log.e(TAG, "Analysis failed", exception)
                            callback.onError(exception)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Unbind previous use cases and bind new ones
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
            
            // Enable low-light optimizations
            enableLowLightOptimizations()
            
        } catch (exception: Exception) {
            Log.e(TAG, "Camera binding failed", exception)
            callback.onError(exception)
        }
    }
    
    private fun enableLowLightOptimizations() {
        camera?.let { camera ->
            val cameraControl = camera.cameraControl
            val cameraInfo = camera.cameraInfo
            
            // Enable night mode if available
            if (cameraInfo.hasFlashUnit()) {
                // Flash will be used automatically when needed due to FLASH_MODE_AUTO
                Log.d(TAG, "Flash unit available for low-light assistance")
            }
            
            // Set exposure compensation for better low-light performance
            val exposureState = cameraInfo.exposureState
            if (exposureState.isExposureCompensationSupported) {
                val exposureIndex = (exposureState.exposureCompensationRange.upper * 0.3f).toInt()
                cameraControl.setExposureCompensationIndex(exposureIndex)
                Log.d(TAG, "Exposure compensation set to: $exposureIndex")
            }
        }
    }
    
    fun captureImage(callback: CameraCallback) {
        val imageCapture = this.imageCapture ?: run {
            callback.onError(IllegalStateException("Image capture not initialized"))
            return
        }
        
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            createImageFile()
        ).build()
        
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Image captured successfully: ${output.savedUri}")
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    callback.onError(exception)
                }
            }
        )
    }
    
    private fun createImageFile(): java.io.File {
        val timeStamp = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        
        return java.io.File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
            "UNDERCARRIAGE_${timeStamp}.jpg"
        )
    }
    
    fun toggleFlashlight() {
        camera?.let { camera ->
            val cameraControl = camera.cameraControl
            val cameraInfo = camera.cameraInfo
            
            if (cameraInfo.hasFlashUnit()) {
                val currentState = cameraInfo.torchState.value ?: TorchState.OFF
                val newState = currentState == TorchState.OFF
                cameraControl.enableTorch(newState)
                Log.d(TAG, "Flashlight toggled: $newState")
            }
        }
    }
    
    fun getCamera(): Camera? = camera
    
    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}