package com.mobileer.audiobridge

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin
//import kotlinx.io.core.* // Import from kotlinx-io
import kotlinx.coroutines.delay
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.time.Duration.Companion.milliseconds

const val BUFFER_SIZE_FRAMES = 256

val audioBridge = AudioBridge()

class SineWaveGenerator(private var frequency: Float,
                        private val amplitude: Float = 1.0f) {
    private var phase = 0.0 // Current phase, maintained between calls
    private var currentSampleRate = 44100
    private var phaseIncrement = 2 * PI * frequency / currentSampleRate

    fun generateBuffer(buffer: FloatArray, numFrames: Int) {
        for (i in 0 until numFrames) {
            val sampleValue = amplitude * sin(phase).toFloat()
            buffer[i] = sampleValue
            phase += phaseIncrement
            // Wrap phase to keep it within a manageable range
            if (phase >= 2 * PI) {
                phase -= 2 * PI
            }
        }
    }

    fun setFrequency(newFrequency: Float) {
        frequency = newFrequency
        updatePhaseIncrement()
    }

    fun setSampleRate(newSampleRate: Int) {
        currentSampleRate = newSampleRate
        updatePhaseIncrement()
    }

    private fun updatePhaseIncrement() {
        phaseIncrement = 2 * PI * frequency / currentSampleRate
    }
}

// Keep a reference to the Job of the audio stream
private var audioStreamJob: Job? = null // Make this private if only App controls it

fun startAudioStream(frequency: Double): Job { // Return the Job
    // Cancel any existing job before starting a new one
    audioStreamJob?.cancel() // This ensures only one stream runs if called multiple times

    val job = GlobalScope.launch(Dispatchers.Default) {
        val leftSine = SineWaveGenerator(frequency.toFloat())
        val rightSine = SineWaveGenerator((frequency * 5.0 / 4.0).toFloat()) // Example: perfect fifth
        val leftBuffer = FloatArray(BUFFER_SIZE_FRAMES)
        val rightBuffer = FloatArray(BUFFER_SIZE_FRAMES)
        val stereoBuffer = FloatArray(BUFFER_SIZE_FRAMES * 2)
        val sampleRate = audioBridge.getSampleRate()
        println("AudioBridge sample rate: $sampleRate")
        leftSine.setSampleRate(sampleRate)
        rightSine.setSampleRate(sampleRate)

        try {
            while (isActive) { // Check isActive for cooperative cancellation
                leftSine.generateBuffer(leftBuffer, BUFFER_SIZE_FRAMES)
                rightSine.generateBuffer(rightBuffer, BUFFER_SIZE_FRAMES)

                // Interleave left and right buffers into stereoBuffer
                for (i in 0 until BUFFER_SIZE_FRAMES) {
                    stereoBuffer[i * 2] = leftBuffer[i]      // Left channel
                    stereoBuffer[i * 2 + 1] = rightBuffer[i] // Right channel
                }

                var framesLeft = BUFFER_SIZE_FRAMES
                var offset = 0
                while (framesLeft > 0 && isActive) { // Also check isActive in inner loop
                    // Ensure audioBridge is open and started before writing
                    // This check might be better placed outside the hot loop or handled by the caller
                    // if (!audioBridge.isStartedAndOpen()) { // Hypothetical check
                    //    delay(100) // Wait and retry or break
                    //    continue
                    // }

                    val frameCount = audioBridge.write(stereoBuffer, offset, framesLeft)
                    if (frameCount < 0) {
                        // Handle error from audioBridge.write, e.g., stream closed
                        println("AudioBridge write error: $frameCount")
                        cancel("AudioBridge write error") // Cancel the coroutine
                        break
                    }
                    offset += frameCount
                    framesLeft -= frameCount
                    if (framesLeft > 0 && isActive) {
                        // Calculate delay more precisely based on frames written
                        // This delay helps prevent busy-waiting if the audio buffer fills up quickly
                        // The original delay was based on framesPerBurst, which might be different
                        // from what was actually written or the buffer capacity.
                        // A small fixed delay or a more dynamic one might be needed.
                        delay(10) // Small delay to yield and prevent tight loop if write is very fast
                        // Or calculate based on buffer status if possible.
                    }
                }
            }
        } catch (e: CancellationException) {
            println("Audio stream coroutine cancelled.")
            // Perform any cleanup specific to this coroutine if needed
        } finally {
            println("Audio stream coroutine finishing.")
            // Ensure resources are released if this coroutine was solely responsible
            // However, audioBridge.stop/close is handled by the button in App
        }
    }
    audioStreamJob = job // Store the new job
    return job
}

// Optional: Add a function to explicitly stop the stream
fun stopAudioStreamJob() {
    audioStreamJob?.cancel()
    audioStreamJob = null
    println("Requested to stop audio stream job.")
}

// App.kt

@Composable
fun App() {
    val frequency = 440.0 // Example frequency
    var isPlaying by remember { mutableStateOf(false) }
    // No need to store the job in Composable state if startAudioStream manages it globally
    // var currentAudioJob by remember { mutableStateOf<Job?>(null) }
    // val coroutineScope = rememberCoroutineScope() // Not strictly needed for this button logic

    Row {
        Button(
            onClick = {
                // Open and start the audio bridge
                // It's important that open() is called before start() and write()
                val openResult = audioBridge.open()
                if (openResult < 0) {
                    println("Failed to open audio bridge: $openResult")
                    // Handle error, maybe show a message to the user
                    return@Button
                }
                val startResult = audioBridge.start()
                if (startResult < 0) {
                    println("Failed to start audio bridge: $startResult")
                    audioBridge.close() // Clean up if start fails
                    return@Button
                }
                println("AudioBridge opened and started.")
                // Start the audio stream job
                startAudioStream(frequency)
                println("Continuous tone started.")
                isPlaying = true
            },
            enabled = !isPlaying
        ) {
            Text("START")
        }

        Button(
            onClick = {
                stopAudioStreamJob()
                // Stop and close the audio bridge
                audioBridge.stop()
                audioBridge.close()
                println("AudioBridge stopped and closed.")
                isPlaying = false
            },
            enabled = isPlaying
        ) {
            Text("STOP")
        }

    }

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup when the Composable leaves the composition
            println("App Composable disposing. Stopping audio stream job and closing audio bridge.")
            stopAudioStreamJob()
            audioBridge.stop() // Ensure bridge is stopped
            audioBridge.close() // Ensure bridge is closed
        }
    }
}
