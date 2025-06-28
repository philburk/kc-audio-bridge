package org.mobileer.audiobridge

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin
//import kotlinx.io.core.* // Import from kotlinx-io
import kotlinx.coroutines.delay
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.time.Duration.Companion.milliseconds

// Configuration
const val SAMPLE_RATE = 44100
const val BUFFER_SIZE_FRAMES = 256

expect class AudioBridge(context: Any? = null) {
    fun open(context: Any? = null,
             sampleRate: Int = SAMPLE_RATE): Int
    fun start(): Int
    fun getChannelCount(): Int
    fun getFramesPerBurst(): Int
    /**
     * Write some audio data to the output stream.
     */
    fun write(buffer: FloatArray,
              offset: Int,
              numFrames: Int): Int
    fun stop()
    fun close()
}

val audioBridge = AudioBridge()

class SineWaveGenerator(private val frequency: Double,
                        private val amplitude: Float = 1.0f) {
    private var phase = 0.0 // Current phase, maintained between calls
    private val phaseIncrement = 2 * PI * frequency / SAMPLE_RATE

    fun generateBuffer(buffer: FloatArray, numFrames: Int) {
        for (i in 0 until numFrames) {
            val sampleValue = amplitude * sin(phase).toFloat()
            buffer[i] = sampleValue
            phase += phaseIncrement
            // Wrap phase to keep it within a manageable range (optional but good practice)
            if (phase >= 2 * PI) {
                phase -= 2 * PI
            }
        }
    }
}

fun startAudioStream(frequency: Double) {
    GlobalScope.launch(Dispatchers.Default) {
        val leftSine = SineWaveGenerator(frequency)
        val rightSine = SineWaveGenerator(frequency * 5.0 / 4.0)
        val leftBuffer = FloatArray(BUFFER_SIZE_FRAMES)
        val rightBuffer = FloatArray(BUFFER_SIZE_FRAMES)
        val stereoBuffer = FloatArray(BUFFER_SIZE_FRAMES * 2)
        while (isActive) { // Use isActive to allow cancellation
            leftSine.generateBuffer(leftBuffer, BUFFER_SIZE_FRAMES)
            rightSine.generateBuffer(rightBuffer, BUFFER_SIZE_FRAMES)

            // Interleave left and right buffers into floatBuffer
            for (i in 0 until BUFFER_SIZE_FRAMES) {
                stereoBuffer[i * 2] = leftBuffer[i]      // Left channel
                stereoBuffer[i * 2 + 1] = rightBuffer[i] // Right channel
            }
            var framesLeft = BUFFER_SIZE_FRAMES // Number of stereo frames
            var offset = 0
            while (framesLeft > 0) {
                var frameCount = audioBridge.write(stereoBuffer, offset, framesLeft)
                offset += frameCount
                framesLeft -= frameCount
                if (framesLeft > 0) {
                    delay((1000 * audioBridge.getFramesPerBurst() / SAMPLE_RATE).milliseconds)
                }
            }
        }
    }
}

@Composable
fun App() {
    val frequency = 440.0 // Example frequency
    var isPlaying by remember { mutableStateOf(false) }

    Column() {
        Button(onClick = {
            if (isPlaying) {
                audioBridge.stop()
                audioBridge.close()
            } else {
                audioBridge.open()
                audioBridge.start()
            }
            isPlaying = !isPlaying
            }) {
            Text(if (isPlaying) "Stop Audio" else "Start Audio")
        }
        Button(onClick = { startAudioStream(frequency) }) {
            Text("Play Continuous Tone")
        }
    }
}
