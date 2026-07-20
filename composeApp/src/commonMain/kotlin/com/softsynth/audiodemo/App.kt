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
import androidx.compose.runtime.setValue
import com.softsynth.audiobridge.AudioBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import com.softsynth.audiobridge.readSuspending
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioDeviceInfo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private var audioBridge: AudioOutputBridge? = null

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
    val MAX_FRAMES_PER_BUFFER = 64
    val STEREO_CHANNELS = 2
    val BASE_FREQUENCY = 440.0 // Concert A for the first sine tone

    // Cancel any existing job before starting a new one
    audioStreamJob?.cancel() // This ensures only one stream runs if called multiple times

    val job = GlobalScope.launch(Dispatchers.Default) {
        val leftSine = SineWaveGenerator(BASE_FREQUENCY.toFloat())
        val rightSine = SineWaveGenerator((BASE_FREQUENCY * 5.0 / 4.0).toFloat())

        val bridge = audioBridge ?: return@launch
        val framesPerBurst = bridge.getFramesPerBurst()
        println("AudioBridge framesPerBurst: $framesPerBurst")
        // Don't make the buffer too large because the note timing will be too grainy.
        val bufferSizeFrames = min(framesPerBurst, MAX_FRAMES_PER_BUFFER)
        val leftBuffer = FloatArray(bufferSizeFrames)
        val rightBuffer = FloatArray(bufferSizeFrames)
        val stereoBuffer = FloatArray(bufferSizeFrames * STEREO_CHANNELS)

        val sampleRate = bridge.getSampleRate()
        println("AudioBridge sample rate: $sampleRate")
        leftSine.setSampleRate(sampleRate)
        rightSine.setSampleRate(sampleRate)

        try {
            while (isActive) { // Check isActive for cooperative cancellation
                leftSine.generateBuffer(leftBuffer, bufferSizeFrames)
                rightSine.generateBuffer(rightBuffer, bufferSizeFrames)

                // Interleave left and right buffers into stereoBuffer
                for (i in 0 until bufferSizeFrames) {
                    stereoBuffer[i * 2] = leftBuffer[i]      // Left channel
                    stereoBuffer[i * 2 + 1] = rightBuffer[i] // Right channel
                }

                // Write the interleaved buffer, waiting up to 1000ms if needed.
                val bridge = audioBridge ?: return@launch
                val framesWritten = bridge.writeSuspending(
                    stereoBuffer,
                    0,
                    bufferSizeFrames,
                    timeoutMillis = 1000L
                )
                if (framesWritten < 0) {
                    // Handle error from audioBridge.write, e.g., stream closed
                    println("AudioBridge write error: $framesWritten")
                    cancel("AudioBridge write error") // Cancel the coroutine
                    break
                } else if (framesWritten < bufferSizeFrames) {
                    println("AudioBridge write timeout")
                    cancel("AudioBridge write timeout") // Cancel the coroutine
                    break
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

fun startAudioDemo(selectedDeviceId: Int, onStarted: (String) -> Unit): AudioResult {
    stopAudioDemo()
    val bridge = AudioOutputBridge.create {
        deviceId = selectedDeviceId
    }
    audioBridge = bridge
    val openResult = bridge.open()
    if (openResult != AudioResult.OK) {
        println("Failed to open audio bridge: $openResult")
        audioBridge = null
        return openResult
    }
    val startResult = bridge.start()
    if (startResult != AudioResult.OK) {
        println("Failed to start audio bridge: $startResult")
        bridge.close()
        audioBridge = null
        return startResult
    }
    onStarted(bridge.getCurrentDeviceName())
    println("AudioBridge opened and started.")
    startAudioStreamJob()
    println("Continuous tone started.")
    return AudioResult.OK
}

fun stopAudioDemo() {
    stopAudioStreamJob()
    audioBridge?.stop()
    audioBridge?.close()
    audioBridge = null
    println("AudioBridge stopped and closed.")
}

// App.kt
private var recordingJob: Job? = null
private var playbackJob: Job? = null
private var recordedAudioData: FloatArray? = null
private var totalRecordedFrames = 0

@Composable
fun App() {
    var isPlaying by remember { mutableStateOf(false) }
    val context = getPlatformContext()
    val scope = rememberCoroutineScope()

    val audioInputSupported = remember { AudioInputBridge.isSupported() }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingRecording by remember { mutableStateOf(false) }
    var framesRecorded by remember { mutableStateOf(0) }
    var averageInputLevel by remember { mutableStateOf(0.0f) }
    var hasRecordingData by remember { mutableStateOf(false) }
    var permissionStatusMessage by remember { mutableStateOf("") }

    // Dynamic Device routing states
    val outputDevices by AudioDeviceManager.outputDevices.collectAsState(initial = emptyList())
    val inputDevices by AudioDeviceManager.inputDevices.collectAsState(initial = emptyList())
    var selectedOutputId by remember { mutableStateOf(-1) }
    var selectedInputId by remember { mutableStateOf(-1) }

    var outputMenuExpanded by remember { mutableStateOf(false) }
    var inputMenuExpanded by remember { mutableStateOf(false) }

    // Active opened device names
    var activeOutputDeviceName by remember { mutableStateOf("None") }
    var activeInputDeviceName by remember { mutableStateOf("None") }

    Column {
        Text("Test AudioBridge on platform ${getPlatform().name}")

        Spacer(modifier = Modifier.height(16.dp))

        // Device Selection Section
        Text("Device Routing Configurations:", style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        
        Row {
            Column {
                Text("Output Device:")
                Box {
                    Button(
                        onClick = { outputMenuExpanded = true },
                        enabled = !isPlaying && !isRecording && !isPlayingRecording
                    ) {
                        val selectedName = if (selectedOutputId == -1) "Default Output Device" else outputDevices.find { it.id == selectedOutputId }?.name ?: "Unknown Device"
                        Text(selectedName)
                    }
                    DropdownMenu(
                        expanded = outputMenuExpanded,
                        onDismissRequest = { outputMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default Output Device") },
                            onClick = {
                                selectedOutputId = -1
                                outputMenuExpanded = false
                            }
                        )
                        outputDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name) },
                                onClick = {
                                    selectedOutputId = device.id
                                    outputMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (audioInputSupported) {
                Column {
                    Text("Input Device:")
                    Box {
                        Button(
                            onClick = { inputMenuExpanded = true },
                            enabled = !isPlaying && !isRecording && !isPlayingRecording
                        ) {
                            val selectedName = if (selectedInputId == -1) "Default Input Device" else inputDevices.find { it.id == selectedInputId }?.name ?: "Unknown Device"
                            Text(selectedName)
                        }
                        DropdownMenu(
                            expanded = inputMenuExpanded,
                            onDismissRequest = { inputMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default Input Device") },
                                onClick = {
                                    selectedInputId = -1
                                    inputMenuExpanded = false
                                }
                            )
                            inputDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.name) },
                                    onClick = {
                                        selectedInputId = device.id
                                        inputMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Audio Output (Sine Wave Demo)", style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Row {
            Button(
                onClick = {
                    val result = startAudioDemo(selectedOutputId) { deviceName ->
                        activeOutputDeviceName = deviceName
                    }
                    if (result != AudioResult.OK) {
                        println("Failed to open audio bridge: $result")
                        return@Button
                    }
                    isPlaying = true
                },
                enabled = !isPlaying && !isRecording && !isPlayingRecording
            ) {
                Text("START")
            }

            Button(
                onClick = {
                    stopAudioDemo()
                    isPlaying = false
                    activeOutputDeviceName = "None"
                },
                enabled = isPlaying
            ) {
                Text("STOP")
            }
        }
        
        if (isPlaying) {
            Text("Active Output Device: $activeOutputDeviceName")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Audio Input (Record & Playback Demo)", style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))

        if (!audioInputSupported) {
            Text("Audio Input is not supported on this platform.", color = androidx.compose.ui.graphics.Color.Red)
        } else {
            Row {
                Button(
                    onClick = {
                        scope.launch {
                            permissionStatusMessage = ""
                            var state = AudioInputBridge.getPermissionState(context)
                            if (state != AudioPermissionState.GRANTED) {
                                permissionStatusMessage = "Requesting permission..."
                                state = AudioInputBridge.requestPermission(context)
                                if (state != AudioPermissionState.GRANTED) {
                                    permissionStatusMessage = "Permission denied."
                                    return@launch
                                }
                            }
                            permissionStatusMessage = "Permission granted."

                            if (isPlaying) {
                                stopAudioDemo()
                                isPlaying = false
                            }

                            recordingJob?.cancel()
                            playbackJob?.cancel()

                            isRecording = true
                            framesRecorded = 0
                            averageInputLevel = 0.0f
                            hasRecordingData = false

                            val inputBridge = AudioInputBridge.create {
                                channels = 1
                                deviceId = selectedInputId
                            }

                            recordingJob = launch(Dispatchers.Default) {
                                val openResult = inputBridge.open()
                                if (openResult != AudioResult.OK) {
                                    println("Failed to open audio input: $openResult")
                                    isRecording = false
                                    return@launch
                                }
                                val startResult = inputBridge.start()
                                if (startResult != AudioResult.OK) {
                                    println("Failed to start audio input: $startResult")
                                    inputBridge.close()
                                    isRecording = false
                                    return@launch
                                }

                                activeInputDeviceName = inputBridge.getCurrentDeviceName()

                                val sampleRate = inputBridge.getSampleRate()
                                val burstSize = inputBridge.getFramesPerBurst()
                                val maxFrames = sampleRate * 10
                                val buffer = FloatArray(maxFrames)
                                recordedAudioData = buffer
                                totalRecordedFrames = 0

                                val tempBuffer = FloatArray(burstSize)

                                try {
                                    while (totalRecordedFrames < maxFrames && isActive) {
                                        val framesToRead = min(burstSize, maxFrames - totalRecordedFrames)
                                        val read = inputBridge.readSuspending(tempBuffer, 0, framesToRead, timeoutMillis = 1000L)
                                        if (read < 0) {
                                            println("AudioInputBridge read error: $read")
                                            break
                                        } else if (read == 0) {
                                            delay(10)
                                            continue
                                        }

                                        tempBuffer.copyInto(
                                            buffer,
                                            destinationOffset = totalRecordedFrames,
                                            startIndex = 0,
                                            endIndex = read
                                        )
                                        totalRecordedFrames += read

                                        var sum = 0.0f
                                        for (i in 0 until read) {
                                            sum += kotlin.math.abs(tempBuffer[i])
                                        }
                                        val avg = if (read > 0) sum / read else 0.0f

                                        framesRecorded = totalRecordedFrames
                                        averageInputLevel = avg
                                    }
                                } finally {
                                    inputBridge.stop()
                                    inputBridge.close()
                                    isRecording = false
                                    activeInputDeviceName = "None"
                                    if (totalRecordedFrames > 0) {
                                        hasRecordingData = true
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isRecording && !isPlayingRecording && !isPlaying
                ) {
                    Text("RECORD")
                }

                Button(
                    onClick = {
                        if (isRecording) {
                            recordingJob?.cancel()
                            recordingJob = null
                        }
                        if (isPlayingRecording) {
                            playbackJob?.cancel()
                            playbackJob = null
                        }
                    },
                    enabled = isRecording || isPlayingRecording
                ) {
                    Text("STOP")
                }

                Button(
                    onClick = {
                        scope.launch {
                            if (!hasRecordingData || recordedAudioData == null) return@launch

                            if (isPlaying) {
                                stopAudioDemo()
                                isPlaying = false
                            }

                            recordingJob?.cancel()
                            playbackJob?.cancel()

                            isPlayingRecording = true

                            playbackJob = launch(Dispatchers.Default) {
                                val bridge = AudioOutputBridge.create {
                                    deviceId = selectedOutputId
                                }
                                val openResult = bridge.open()
                                if (openResult != AudioResult.OK) {
                                    println("Failed to open audio output: $openResult")
                                    isPlayingRecording = false
                                    return@launch
                                }
                                val startResult = bridge.start()
                                if (startResult != AudioResult.OK) {
                                    println("Failed to start audio output: $startResult")
                                    bridge.close()
                                    isPlayingRecording = false
                                    return@launch
                                }

                                activeOutputDeviceName = bridge.getCurrentDeviceName()
                                val burstSize = bridge.getFramesPerBurst()
                                val data = recordedAudioData ?: return@launch
                                val totalFrames = totalRecordedFrames
                                var playOffset = 0
                                val tempBuffer = FloatArray(burstSize * 2)

                                try {
                                    while (playOffset < totalFrames && isActive) {
                                        val framesToWrite = min(burstSize, totalFrames - playOffset)
                                        for (i in 0 until framesToWrite) {
                                            val sample = data[playOffset + i]
                                            tempBuffer[i * 2] = sample
                                            tempBuffer[i * 2 + 1] = sample
                                        }
                                        val written = bridge.writeSuspending(tempBuffer, 0, framesToWrite, timeoutMillis = 1000L)
                                        if (written < 0) {
                                            println("AudioBridge write error: $written")
                                            break
                                        } else if (written == 0) {
                                            delay(10)
                                            continue
                                        }
                                        playOffset += written
                                    }
                                } finally {
                                    bridge.stop()
                                    bridge.close()
                                    isPlayingRecording = false
                                    activeOutputDeviceName = "None"
                                }
                            }
                        }
                    },
                    enabled = hasRecordingData && !isRecording && !isPlayingRecording && !isPlaying
                ) {
                    Text("PLAY")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isRecording) {
                Text("Status: Recording on active device $activeInputDeviceName")
            } else if (isPlayingRecording) {
                Text("Status: Playing recording on active device $activeOutputDeviceName")
            } else {
                Text("Status: Idle")
            }

            Text("Frames Recorded: $framesRecorded")
            val roundedLevel = ((averageInputLevel * 1000).toInt() / 1000.0f)
            Text("Average Input Level: $roundedLevel")
            if (permissionStatusMessage.isNotEmpty()) {
                Text("Permission: $permissionStatusMessage")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            println("App Composable disposing. Stopping jobs and closing active audio bridges.")
            stopAudioStreamJob()
            recordingJob?.cancel()
            playbackJob?.cancel()
            audioBridge?.stop()
            audioBridge?.close()
        }
    }
}
