/*
 * Copyright 2025 Phil Burk, Mobileer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.audiodemo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

class SineWaveGenerator(private var frequency: Float,
                        private val amplitude: Float = 1.0f) {

    private var phase = 0.0
    private var currentSampleRate = 44100
    private var phaseIncrement = 2 * PI * frequency / currentSampleRate

    fun generateBuffer(buffer: FloatArray, numFrames: Int) {
        for (i in 0 until numFrames) {
            val sampleValue = amplitude * sin(phase).toFloat()
            buffer[i] = sampleValue
            phase += phaseIncrement
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

@Composable
fun SineWaveDemo(
    selectedOutputId: Int,
    isEnabled: Boolean,
    onPlayingChanged: (isPlaying: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var activeOutputDeviceName by remember { mutableStateOf("None") }

    var audioBridge by remember { mutableStateOf<AudioOutputBridge?>(null) }
    var audioStreamJob by remember { mutableStateOf<Job?>(null) }

    fun stopAudioStream() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        audioBridge?.stop()
        audioBridge?.close()
        audioBridge = null
        isPlaying = false
        activeOutputDeviceName = "None"
        onPlayingChanged(false)
    }

    fun startAudioStream() {
        stopAudioStream()

        val bridge = AudioOutputBridge.create {
            deviceId = selectedOutputId
        }
        audioBridge = bridge

        val openResult = bridge.open()
        if (openResult != AudioResult.OK) {
            println("Failed to open audio bridge: $openResult")
            audioBridge = null
            return
        }

        val startResult = bridge.start()
        if (startResult != AudioResult.OK) {
            println("Failed to start audio bridge: $startResult")
            bridge.close()
            audioBridge = null
            return
        }

        isPlaying = true
        activeOutputDeviceName = bridge.getCurrentDeviceName()
        onPlayingChanged(true)

        val MAX_FRAMES_PER_BUFFER = 64
        val STEREO_CHANNELS = 2
        val BASE_FREQUENCY = 440.0

        audioStreamJob = scope.launch(Dispatchers.Default) {
            val leftSine = SineWaveGenerator(BASE_FREQUENCY.toFloat(),
                amplitude = 0.1f)
            val rightSine = SineWaveGenerator((BASE_FREQUENCY * 5.0 / 4.0).toFloat(),
                amplitude = 0.1f)
            val framesPerBurst = bridge.getFramesPerBurst()
            val bufferSizeFrames = min(framesPerBurst, MAX_FRAMES_PER_BUFFER)
            val leftBuffer = FloatArray(bufferSizeFrames)
            val rightBuffer = FloatArray(bufferSizeFrames)
            val stereoBuffer = FloatArray(bufferSizeFrames * STEREO_CHANNELS)

            val sampleRate = bridge.getSampleRate()
            leftSine.setSampleRate(sampleRate)
            rightSine.setSampleRate(sampleRate)

            try {
                while (isActive) {
                    leftSine.generateBuffer(leftBuffer, bufferSizeFrames)
                    rightSine.generateBuffer(rightBuffer, bufferSizeFrames)

                    for (i in 0 until bufferSizeFrames) {
                        stereoBuffer[i * 2] = leftBuffer[i]
                        stereoBuffer[i * 2 + 1] = rightBuffer[i]
                    }

                    val framesWritten = bridge.writeSuspending(
                        stereoBuffer,
                        0,
                        bufferSizeFrames,
                        timeoutMillis = 1000L
                    )
                    if (framesWritten < 0) {
                        println("AudioBridge write error: $framesWritten")
                        cancel("AudioBridge write error")
                        break
                    } else if (framesWritten < bufferSizeFrames) {
                        println("AudioBridge write timeout")
                        cancel("AudioBridge write timeout")
                        break
                    }
                }
            } catch (e: CancellationException) {
                println("Audio stream coroutine cancelled.")
            } finally {
                println("Audio stream coroutine finishing.")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioStreamJob?.cancel()
            audioBridge?.stop()
            audioBridge?.close()
        }
    }

    Column {
        Text(
            "Audio Output (Sine Wave Demo)",
            style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
        Row {
            Button(
                onClick = { startAudioStream() },
                enabled = isEnabled && !isPlaying
            ) {
                Text("START")
            }

            Button(
                onClick = { stopAudioStream() },
                enabled = isPlaying
            ) {
                Text("STOP")
            }
        }

        if (isPlaying) {
            Text("Active Output Device: $activeOutputDeviceName")
        }
    }
}
