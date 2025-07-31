package com.undercarriage.scanner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

class SonicFeedbackManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SonicFeedbackManager"
        private const val MAX_STREAMS = 3
        private const val BASE_FREQUENCY = 440f // A4 note
        private const val DISTANCE_SCALE_FACTOR = 10f
    }
    
    private var soundPool: SoundPool? = null
    private var vibrator: Vibrator? = null
    private var feedbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Sound IDs for different tones
    private var leftBeepSoundId: Int = 0
    private var rightBeepSoundId: Int = 0
    private var centerBeepSoundId: Int = 0
    private var completeBeepSoundId: Int = 0
    
    data class GuidanceDirection(
        val deltaX: Float, // Horizontal offset (-1 left, +1 right)
        val deltaY: Float, // Vertical offset (-1 down, +1 up)
        val distance: Float, // Distance to target position (0-1)
        val isStable: Boolean = false
    )
    
    fun initialize() {
        try {
            // Initialize SoundPool
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(audioAttributes)
                .build()
            
            // Initialize Vibrator
            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            // Load sound effects (these would be actual audio files in production)
            loadSoundEffects()
            
            Log.d(TAG, "SonicFeedbackManager initialized successfully")
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to initialize SonicFeedbackManager", exception)
        }
    }
    
    private fun loadSoundEffects() {
        // In a real implementation, you would load actual audio files
        // For now, we'll simulate different beep sounds with different IDs
        // You would create these sound files or generate them programmatically
        
        // soundPool?.load(context, R.raw.beep_left, 1)
        // soundPool?.load(context, R.raw.beep_right, 1)
        // soundPool?.load(context, R.raw.beep_center, 1)
        // soundPool?.load(context, R.raw.beep_complete, 1)
        
        leftBeepSoundId = 1
        rightBeepSoundId = 2
        centerBeepSoundId = 3
        completeBeepSoundId = 4
    }
    
    fun provideDirectionalGuidance(direction: GuidanceDirection) {
        feedbackScope.launch {
            try {
                when {
                    direction.isStable -> {
                        playStabilityFeedback()
                        vibratePattern(VibrationPattern.STABLE)
                    }
                    direction.distance > 0.8f -> {
                        playDistanceFeedback(direction, isClose = false)
                        vibratePattern(VibrationPattern.FAR)
                    }
                    direction.distance > 0.3f -> {
                        playDirectionalBeep(direction)
                        vibratePattern(VibrationPattern.MEDIUM)
                    }
                    else -> {
                        playProximityFeedback(direction)
                        vibratePattern(VibrationPattern.CLOSE)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error providing directional guidance", exception)
            }
        }
    }
    
    private fun playDirectionalBeep(direction: GuidanceDirection) {
        val soundPool = this.soundPool ?: return
        
        // Calculate stereo panning (-1.0 = left, 1.0 = right)
        val pan = direction.deltaX.coerceIn(-1f, 1f)
        
        // Calculate pitch based on distance (closer = higher pitch)
        val pitch = 0.5f + (1f - direction.distance) * 0.5f
        
        // Calculate volume based on distance
        val volume = 0.3f + (1f - direction.distance) * 0.7f
        
        val soundId = when {
            abs(direction.deltaX) < 0.1f -> centerBeepSoundId
            direction.deltaX < 0 -> leftBeepSoundId
            else -> rightBeepSoundId
        }
        
        // Play the sound with spatial audio
        soundPool.play(
            soundId,
            volume, // left volume
            volume, // right volume
            1, // priority
            0, // loop (0 = no loop)
            pitch // playback rate/pitch
        )
        
        Log.d(TAG, "Played directional beep: pan=$pan, pitch=$pitch, volume=$volume")
    }
    
    private fun playDistanceFeedback(direction: GuidanceDirection, isClose: Boolean) {
        // Play slower beeps for far distances, faster for close
        val interval = if (isClose) 200L else 800L
        
        feedbackScope.launch {
            repeat(3) {
                playDirectionalBeep(direction)
                delay(interval)
            }
        }
    }
    
    private fun playProximityFeedback(direction: GuidanceDirection) {
        // Rapid beeping for very close positioning
        feedbackScope.launch {
            repeat(5) {
                playDirectionalBeep(direction.copy(distance = 0.1f))
                delay(100L)
            }
        }
    }
    
    private fun playStabilityFeedback() {
        soundPool?.play(completeBeepSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        Log.d(TAG, "Played stability confirmation beep")
    }
    
    fun playCompletionFeedback() {
        feedbackScope.launch {
            // Play ascending tone sequence
            repeat(3) { index ->
                val pitch = 1.0f + (index * 0.2f)
                soundPool?.play(completeBeepSoundId, 0.9f, 0.9f, 1, 0, pitch)
                delay(200L)
            }
            
            // Victory vibration
            vibratePattern(VibrationPattern.COMPLETE)
        }
    }
    
    fun playErrorFeedback() {
        feedbackScope.launch {
            // Play descending error tone
            repeat(2) { index ->
                val pitch = 1.0f - (index * 0.3f)
                soundPool?.play(leftBeepSoundId, 0.7f, 0.7f, 1, 0, pitch)
                delay(150L)
            }
            
            vibratePattern(VibrationPattern.ERROR)
        }
    }
    
    private enum class VibrationPattern {
        STABLE, FAR, MEDIUM, CLOSE, COMPLETE, ERROR
    }
    
    private fun vibratePattern(pattern: VibrationPattern) {
        val vibrator = this.vibrator ?: return
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                VibrationPattern.STABLE -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                VibrationPattern.FAR -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1)
                VibrationPattern.MEDIUM -> VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
                VibrationPattern.CLOSE -> VibrationEffect.createWaveform(longArrayOf(0, 50, 25, 50, 25, 50), -1)
                VibrationPattern.COMPLETE -> VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 300, 100, 300), -1)
                VibrationPattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (pattern) {
                VibrationPattern.STABLE -> vibrator.vibrate(100)
                VibrationPattern.FAR -> vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
                VibrationPattern.MEDIUM -> vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
                VibrationPattern.CLOSE -> vibrator.vibrate(longArrayOf(0, 50, 25, 50, 25, 50), -1)
                VibrationPattern.COMPLETE -> vibrator.vibrate(longArrayOf(0, 300, 100, 300, 100, 300), -1)
                VibrationPattern.ERROR -> vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }
    }
    
    fun setVolume(volume: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        val targetVolume = (maxVolume * volume.coerceIn(0f, 1f)).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVolume, 0)
    }
    
    fun shutdown() {
        try {
            feedbackScope.cancel()
            soundPool?.release()
            soundPool = null
            Log.d(TAG, "SonicFeedbackManager shutdown completed")
        } catch (exception: Exception) {
            Log.e(TAG, "Error during shutdown", exception)
        }
    }
}