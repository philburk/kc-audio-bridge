package com.mobileer.audiobridge

//import kotlinx.io.core.* // Import from kotlinx-io
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin


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

fun startAudioStreamJob(): Job { // Return the Job
    val MAX_FRAMES_PER_BUFFER = 256
    val STEREO_CHANNELS = 2
    val BASE_FREQUENCY = 440.0 // Concert A for the first sine tone

    // Cancel any existing job before starting a new one
    audioStreamJob?.cancel() // This ensures only one stream runs if called multiple times

    val job = GlobalScope.launch(Dispatchers.Default) {
        val leftSine = SineWaveGenerator(BASE_FREQUENCY.toFloat())
        val rightSine = SineWaveGenerator((BASE_FREQUENCY * 5.0 / 4.0).toFloat())

        val framesPerBurst = audioBridge.getFramesPerBurst()
        println("AudioBridge framesPerBurst: $framesPerBurst")
        // Don't make the buffer too large because the note timing will be too grainy.
        val bufferSizeFrames = min(framesPerBurst, MAX_FRAMES_PER_BUFFER)
        val leftBuffer = FloatArray(bufferSizeFrames)
        val rightBuffer = FloatArray(bufferSizeFrames)
        val stereoBuffer = FloatArray(bufferSizeFrames * STEREO_CHANNELS)

        val sampleRate = audioBridge.getSampleRate()
        println("AudioBridge sample rate: $sampleRate")
        leftSine.setSampleRate(sampleRate)
        rightSine.setSampleRate(sampleRate)

        // Set time to sleep based on the audio burst size.
        val burstMillis = 1000L * bufferSizeFrames / sampleRate

        try {
            while (isActive) { // Check isActive for cooperative cancellation
                leftSine.generateBuffer(leftBuffer, bufferSizeFrames)
                rightSine.generateBuffer(rightBuffer, bufferSizeFrames)

                // Interleave left and right buffers into stereoBuffer
                for (i in 0 until bufferSizeFrames) {
                    stereoBuffer[i * 2] = leftBuffer[i]      // Left channel
                    stereoBuffer[i * 2 + 1] = rightBuffer[i] // Right channel
                }

                var framesLeft = bufferSizeFrames
                var offset = 0
                while (framesLeft > 0 && isActive) {
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
                        // Wait long enough for one burst of room to be available.
                        delay(burstMillis)
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

fun startAudioDemo(): AudioResult {
    // Open and start the audio bridge
    // It's important that open() is called before start() and write()
    val openResult = audioBridge.open()
    if (openResult != AudioResult.OK) {
        println("Failed to open audio bridge: $openResult")
        // Handle error, maybe show a message to the user
        return openResult
    }
    val startResult = audioBridge.start()
    if (openResult != AudioResult.OK) {
        println("Failed to start audio bridge: $startResult")
        audioBridge.close() // Clean up if start fails
        return openResult
    }
    println("AudioBridge opened and started.")
    // Start the audio stream job
    startAudioStreamJob()
    println("Continuous tone started.")
    return AudioResult.OK
}

fun stopAudioDemo() {
    stopAudioStreamJob()
    // Stop and close the audio bridge
    audioBridge.stop()
    audioBridge.close()
    println("AudioBridge stopped and closed.")
}

// App.kt
@Composable
fun App() {
    var isPlaying by remember { mutableStateOf(false) }

    Row {
        Button(
            onClick = {
                val result = startAudioDemo()
                if (result != AudioResult.OK) {
                    println("Failed to open audio bridge: $result")
                    // Handle error, maybe show a message to the user
                    return@Button
                }
                isPlaying = true
            },
            enabled = !isPlaying
        ) {
            Text("START")
        }

        Button(
            onClick = {
                stopAudioDemo()
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
