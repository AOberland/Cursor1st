package com.undercarriage.scanner.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.*

class CoverageAnalyzer {
    
    companion object {
        private const val TAG = "CoverageAnalyzer"
        private const val ORB_MAX_FEATURES = 500
        private const val COVERAGE_THRESHOLD = 0.85f
        private const val STABILITY_THRESHOLD = 0.5f // degrees
        private const val MIN_MATCH_DISTANCE = 50f
    }
    
    private val orb = ORB.create(ORB_MAX_FEATURES)
    private var previousFrame: Mat? = null
    private var previousKeypoints: MatOfKeyPoint? = null
    private var previousDescriptors: Mat? = null
    private var coverageMap: Mat? = null
    private var totalCoverage = 0.0
    
    data class AnalysisResult(
        val deltaX: Float,
        val deltaY: Float,
        val rotation: Float,
        val coverage: Float,
        val isStable: Boolean,
        val shouldCapture: Boolean,
        val gapAreas: List<Rect2d> = emptyList()
    )
    
    data class Rect2d(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double
    )
    
    fun analyzeFrame(imageProxy: ImageProxy): AnalysisResult {
        return try {
            val currentFrame = convertImageProxyToMat(imageProxy)
            val result = processFrame(currentFrame)
            
            // Update previous frame for next analysis
            if (previousFrame == null) {
                previousFrame = Mat()
            }
            currentFrame.copyTo(previousFrame!!)
            
            result
        } catch (exception: Exception) {
            Log.e(TAG, "Error analyzing frame", exception)
            AnalysisResult(0f, 0f, 0f, 0f, false, false)
        }
    }
    
    private fun convertImageProxyToMat(imageProxy: ImageProxy): Mat {
        val buffer = imageProxy.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // Convert to grayscale for feature detection
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
        
        return grayMat
    }
    
    private fun processFrame(currentFrame: Mat): AnalysisResult {
        val previousFrame = this.previousFrame
        
        // Initialize coverage map if first frame
        if (coverageMap == null) {
            coverageMap = Mat.zeros(currentFrame.size(), CvType.CV_8UC1)
        }
        
        // If no previous frame, initialize and return
        if (previousFrame == null) {
            detectAndStoreFeatures(currentFrame)
            return AnalysisResult(0f, 0f, 0f, 0f, false, false)
        }
        
        // Detect features in current frame
        val currentKeypoints = MatOfKeyPoint()
        val currentDescriptors = Mat()
        orb.detectAndCompute(currentFrame, Mat(), currentKeypoints, currentDescriptors)
        
        // Calculate movement if we have previous features
        val movement = calculateMovement(currentKeypoints, currentDescriptors)
        
        // Update coverage map
        updateCoverageMap(currentFrame, movement)
        
        // Calculate total coverage
        val coverage = calculateTotalCoverage()
        
        // Detect gaps
        val gaps = detectGaps()
        
        // Determine if camera is stable
        val isStable = isMovementStable(movement)
        
        // Determine if we should capture
        val shouldCapture = shouldCaptureFrame(movement, coverage, isStable)
        
        // Store current features for next frame
        storeCurrentFeatures(currentKeypoints, currentDescriptors)
        
        return AnalysisResult(
            deltaX = movement.deltaX,
            deltaY = movement.deltaY,
            rotation = movement.rotation,
            coverage = coverage,
            isStable = isStable,
            shouldCapture = shouldCapture,
            gapAreas = gaps
        )
    }
    
    private fun detectAndStoreFeatures(frame: Mat) {
        if (previousKeypoints == null) {
            previousKeypoints = MatOfKeyPoint()
            previousDescriptors = Mat()
        }
        
        orb.detectAndCompute(frame, Mat(), previousKeypoints!!, previousDescriptors!!)
    }
    
    private fun storeCurrentFeatures(keypoints: MatOfKeyPoint, descriptors: Mat) {
        if (previousKeypoints == null) {
            previousKeypoints = MatOfKeyPoint()
        }
        if (previousDescriptors == null) {
            previousDescriptors = Mat()
        }
        
        keypoints.copyTo(previousKeypoints!!)
        descriptors.copyTo(previousDescriptors!!)
    }
    
    data class Movement(
        val deltaX: Float,
        val deltaY: Float,
        val rotation: Float,
        val confidence: Float
    )
    
    private fun calculateMovement(currentKeypoints: MatOfKeyPoint, currentDescriptors: Mat): Movement {
        val previousKeypoints = this.previousKeypoints
        val previousDescriptors = this.previousDescriptors
        
        if (previousKeypoints == null || previousDescriptors == null || 
            previousDescriptors.empty() || currentDescriptors.empty()) {
            return Movement(0f, 0f, 0f, 0f)
        }
        
        try {
            // Match features between frames
            val matcher = org.opencv.features2d.DescriptorMatcher.create(org.opencv.features2d.DescriptorMatcher.BRUTEFORCE_HAMMING)
            val matches = mutableListOf<org.opencv.core.DMatch>()
            matcher.match(previousDescriptors, currentDescriptors, matches)
            
            if (matches.size < 10) {
                return Movement(0f, 0f, 0f, 0f)
            }
            
            // Filter good matches
            val goodMatches = matches.filter { it.distance < MIN_MATCH_DISTANCE }
            
            if (goodMatches.size < 5) {
                return Movement(0f, 0f, 0f, 0f)
            }
            
            // Calculate average movement
            val prevPoints = previousKeypoints.toArray()
            val currPoints = currentKeypoints.toArray()
            
            var totalDeltaX = 0.0
            var totalDeltaY = 0.0
            var validMatches = 0
            
            for (match in goodMatches) {
                if (match.queryIdx < prevPoints.size && match.trainIdx < currPoints.size) {
                    val prevPoint = prevPoints[match.queryIdx]
                    val currPoint = currPoints[match.trainIdx]
                    
                    totalDeltaX += currPoint.pt.x - prevPoint.pt.x
                    totalDeltaY += currPoint.pt.y - prevPoint.pt.y
                    validMatches++
                }
            }
            
            if (validMatches == 0) {
                return Movement(0f, 0f, 0f, 0f)
            }
            
            val avgDeltaX = (totalDeltaX / validMatches).toFloat()
            val avgDeltaY = (totalDeltaY / validMatches).toFloat()
            val confidence = (validMatches.toFloat() / matches.size).coerceIn(0f, 1f)
            
            // Calculate rotation (simplified)
            val rotation = calculateRotation(goodMatches, prevPoints, currPoints)
            
            return Movement(avgDeltaX, avgDeltaY, rotation, confidence)
            
        } catch (exception: Exception) {
            Log.e(TAG, "Error calculating movement", exception)
            return Movement(0f, 0f, 0f, 0f)
        }
    }
    
    private fun calculateRotation(matches: List<org.opencv.core.DMatch>, 
                                 prevPoints: Array<org.opencv.core.KeyPoint>, 
                                 currPoints: Array<org.opencv.core.KeyPoint>): Float {
        if (matches.size < 2) return 0f
        
        try {
            var totalRotation = 0.0
            var validRotations = 0
            
            for (i in 0 until minOf(matches.size - 1, 10)) {
                val match1 = matches[i]
                val match2 = matches[i + 1]
                
                if (match1.queryIdx < prevPoints.size && match1.trainIdx < currPoints.size &&
                    match2.queryIdx < prevPoints.size && match2.trainIdx < currPoints.size) {
                    
                    val prev1 = prevPoints[match1.queryIdx].pt
                    val curr1 = currPoints[match1.trainIdx].pt
                    val prev2 = prevPoints[match2.queryIdx].pt
                    val curr2 = currPoints[match2.trainIdx].pt
                    
                    val prevAngle = atan2(prev2.y - prev1.y, prev2.x - prev1.x)
                    val currAngle = atan2(curr2.y - curr1.y, curr2.x - curr1.x)
                    
                    var rotation = currAngle - prevAngle
                    if (rotation > PI) rotation -= 2 * PI
                    if (rotation < -PI) rotation += 2 * PI
                    
                    totalRotation += rotation
                    validRotations++
                }
            }
            
            return if (validRotations > 0) {
                Math.toDegrees(totalRotation / validRotations).toFloat()
            } else {
                0f
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Error calculating rotation", exception)
            return 0f
        }
    }
    
    private fun updateCoverageMap(currentFrame: Mat, movement: Movement) {
        val coverageMap = this.coverageMap ?: return
        
        try {
            // Create a mask for the current frame area
            val mask = Mat.ones(currentFrame.size(), CvType.CV_8UC1)
            
            // Apply movement compensation (simplified)
            val transformMatrix = Mat.eye(2, 3, CvType.CV_32F)
            transformMatrix.put(0, 2, -movement.deltaX.toDouble())
            transformMatrix.put(1, 2, -movement.deltaY.toDouble())
            
            val alignedMask = Mat()
            Imgproc.warpAffine(mask, alignedMask, transformMatrix, coverageMap.size())
            
            // Update coverage map
            Core.bitwise_or(coverageMap, alignedMask, coverageMap)
            
        } catch (exception: Exception) {
            Log.e(TAG, "Error updating coverage map", exception)
        }
    }
    
    private fun calculateTotalCoverage(): Float {
        val coverageMap = this.coverageMap ?: return 0f
        
        return try {
            val totalPixels = coverageMap.total()
            val coveredPixels = Core.countNonZero(coverageMap)
            (coveredPixels.toFloat() / totalPixels.toFloat()).coerceIn(0f, 1f)
        } catch (exception: Exception) {
            Log.e(TAG, "Error calculating coverage", exception)
            0f
        }
    }
    
    private fun detectGaps(): List<Rect2d> {
        val coverageMap = this.coverageMap ?: return emptyList()
        
        return try {
            // Invert coverage map to find gaps
            val gapMap = Mat()
            Core.bitwise_not(coverageMap, gapMap)
            
            // Find contours of gaps
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(gapMap, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Convert contours to rectangles
            contours.mapNotNull { contour ->
                val rect = Imgproc.boundingRect(contour)
                if (rect.area() > 100) { // Filter small gaps
                    Rect2d(rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
                } else {
                    null
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Error detecting gaps", exception)
            emptyList()
        }
    }
    
    private fun isMovementStable(movement: Movement): Boolean {
        val totalMovement = sqrt(movement.deltaX * movement.deltaX + movement.deltaY * movement.deltaY)
        return totalMovement < STABILITY_THRESHOLD && abs(movement.rotation) < STABILITY_THRESHOLD
    }
    
    private fun shouldCaptureFrame(movement: Movement, coverage: Float, isStable: Boolean): Boolean {
        // Capture if stable and coverage is below threshold
        return isStable && coverage < COVERAGE_THRESHOLD
    }
    
    fun getCoverageHeatmap(): Bitmap? {
        val coverageMap = this.coverageMap ?: return null
        
        return try {
            // Apply color map to coverage data
            val colorMap = Mat()
            Imgproc.applyColorMap(coverageMap, colorMap, Imgproc.COLORMAP_JET)
            
            // Convert to bitmap
            val bitmap = Bitmap.createBitmap(colorMap.cols(), colorMap.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(colorMap, bitmap)
            bitmap
        } catch (exception: Exception) {
            Log.e(TAG, "Error creating coverage heatmap", exception)
            null
        }
    }
    
    fun isComplete(): Boolean {
        return totalCoverage >= COVERAGE_THRESHOLD
    }
    
    fun reset() {
        previousFrame?.release()
        previousKeypoints?.release()
        previousDescriptors?.release()
        coverageMap?.release()
        
        previousFrame = null
        previousKeypoints = null
        previousDescriptors = null
        coverageMap = null
        totalCoverage = 0.0
        
        Log.d(TAG, "Coverage analyzer reset")
    }
}