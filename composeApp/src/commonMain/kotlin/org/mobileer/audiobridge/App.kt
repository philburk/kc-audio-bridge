package org.mobileer.audiobridge

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.io.* // Import from kotlinx-io
//import kotlinx.io.core.* // Import from kotlinx-io
import kotlinx.coroutines.delay
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.time.Duration.Companion.milliseconds

external fun playStereoFrame(left: Float, right: Float)
external fun showJavaScriptAlert()
external fun setNoiseLevel(level: Float)

// Configuration
const val SAMPLE_RATE = 44100
const val BUFFER_SIZE = 2048 // Adjust buffer size as needed

fun generateSineWaveBuffer(frequency: Double, amplitude: Float = 1.0f): FloatArray {
    val data = FloatArray(BUFFER_SIZE)
    for (i in 0 until BUFFER_SIZE) {
        val time = i.toDouble() / SAMPLE_RATE
        val value = amplitude * sin(2 * PI * frequency * time).toFloat()
        data[i] = value
    }
    return data
}

fun startAudioStream(frequency: Double) {
    GlobalScope.launch(Dispatchers.Default) {
        while (true) {
            val floatBuffer = generateSineWaveBuffer(frequency)
            for (left: Float in floatBuffer) {
                playStereoFrame(left, 0.0f)
            }
            delay((1000 * BUFFER_SIZE / SAMPLE_RATE).milliseconds)
        }
    }
}

expect class AudioBridge(context: Any? = null) {
    fun write(buffer: FloatArray, numFrames: Int)
}

@JsExport
@JsName("getFunnyText")
fun getFunnyText() = "Hello Mars"

val audioBridge = AudioBridge()

@Composable
fun App() {
    val frequency = 440.0 // Example frequency

    Column() {
        Button(onClick = {
            val buffer = FloatArray(60)
            val random = Random.Default
            for (i in buffer.indices) {
                buffer[i] = random.nextFloat() * 2.0f - 1.0f // Random values between -1.0 and +1.0
            }
            audioBridge.write(buffer, 60)
            }) {
            Text("Write noise")
        }
        Button(onClick = { showJavaScriptAlert() }) {
            Text("Show JavaScript Alert")
        }
        Button(onClick = { startAudioStream(frequency) }) {
            Text("Play Continuous Tone")
        }
        Button(onClick = {
            val random = Random.Default
            val randomLevel = random.nextFloat()
            setNoiseLevel(randomLevel)
        }) {
            Text("Set random level")
        }
        Text(getFunnyText())
    }
}
