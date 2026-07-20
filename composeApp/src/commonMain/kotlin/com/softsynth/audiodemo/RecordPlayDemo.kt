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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.readSuspending
import com.softsynth.audiobridge.writeSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun RecordPlayDemo(
    selectedInputId: Int,
    selectedOutputId: Int,
    isEnabled: Boolean,
    onStateChanged: (isRunning: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = getPlatformContext()
    val audioInputSupported = remember { AudioInputBridge.isSupported() }

    var isRecording by remember { mutableStateOf(false) }
    var isPlayingRecording by remember { mutableStateOf(false) }
    var framesRecorded by remember { mutableStateOf(0) }
    var framesPlayed by remember { mutableStateOf(0) }
    var averageInputLevel by remember { mutableStateOf(0.0f) }
    var hasRecordingData by remember { mutableStateOf(false) }
    var permissionStatusMessage by remember { mutableStateOf("") }

    var activeInputDeviceName by remember { mutableStateOf("None") }
    var activeOutputDeviceName by remember { mutableStateOf("None") }

    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    // Recorded audio buffer state
    var recordedAudioData by remember { mutableStateOf<FloatArray?>(null) }
    var totalRecordedFrames by remember { mutableStateOf(0) }

    fun stopAll() {
        recordingJob?.cancel()
        recordingJob = null
        playbackJob?.cancel()
        playbackJob = null
        isRecording = false
        isPlayingRecording = false
        activeInputDeviceName = "None"
        activeOutputDeviceName = "None"
        onStateChanged(false)
    }

    fun startRecording() {
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

            stopAll()
            isRecording = true
            framesRecorded = 0
            framesPlayed = 0
            averageInputLevel = 0.0f
            hasRecordingData = false
            onStateChanged(true)

            val inputBridge = AudioInputBridge.create {
                channels = 1
                deviceId = selectedInputId
            }

            recordingJob = scope.launch(Dispatchers.Default) {
                val openResult = inputBridge.open()
                if (openResult != AudioResult.OK) {
                    println("Failed to open audio input: $openResult")
                    isRecording = false
                    onStateChanged(false)
                    return@launch
                }
                val startResult = inputBridge.start()
                if (startResult != AudioResult.OK) {
                    println("Failed to start audio input: $startResult")
                    inputBridge.close()
                    isRecording = false
                    onStateChanged(false)
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
                        framesRecorded = totalRecordedFrames

                        var sum = 0.0f
                        for (i in 0 until read) {
                            sum += kotlin.math.abs(tempBuffer[i])
                        }
                        val avg = if (read > 0) sum / read else 0.0f
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
                    onStateChanged(isPlayingRecording)
                }
            }
        }
    }

    fun startPlayback() {
        scope.launch {
            if (!hasRecordingData || recordedAudioData == null) return@launch

            stopAll()
            isPlayingRecording = true
            framesPlayed = 0
            onStateChanged(true)

            val playbackData = recordedAudioData ?: return@launch
            val playbackFramesCount = totalRecordedFrames

            playbackJob = scope.launch(Dispatchers.Default) {
                val bridge = AudioOutputBridge.create {
                    deviceId = selectedOutputId
                }
                val openResult = bridge.open()
                if (openResult != AudioResult.OK) {
                    println("Failed to open audio output: $openResult")
                    isPlayingRecording = false
                    onStateChanged(false)
                    return@launch
                }
                val startResult = bridge.start()
                if (startResult != AudioResult.OK) {
                    println("Failed to start audio output: $startResult")
                    bridge.close()
                    isPlayingRecording = false
                    onStateChanged(false)
                    return@launch
                }

                activeOutputDeviceName = bridge.getCurrentDeviceName()
                val burstSize = bridge.getFramesPerBurst()
                var playOffset = 0
                val tempBuffer = FloatArray(burstSize * 2)

                try {
                    while (playOffset < playbackFramesCount && isActive) {
                        val framesToWrite = min(burstSize, playbackFramesCount - playOffset)
                        for (i in 0 until framesToWrite) {
                            val sample = playbackData[playOffset + i]
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
                        framesPlayed = playOffset
                    }
                } finally {
                    bridge.stop()
                    bridge.close()
                    isPlayingRecording = false
                    activeOutputDeviceName = "None"
                    onStateChanged(isRecording)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
            playbackJob?.cancel()
        }
    }

    Column {
        Text(
            "Audio Input (Record & Playback Demo)",
            style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )

        if (!audioInputSupported) {
            Text("Audio Input is not supported on this platform.", color = androidx.compose.ui.graphics.Color.Red)
        } else {
            Row {
                Button(
                    onClick = { startRecording() },
                    enabled = isEnabled && !isRecording && !isPlayingRecording
                ) {
                    Text("RECORD")
                }

                Button(
                    onClick = { stopAll() },
                    enabled = isRecording || isPlayingRecording
                ) {
                    Text("STOP")
                }

                Button(
                    onClick = { startPlayback() },
                    enabled = isEnabled && hasRecordingData && !isRecording && !isPlayingRecording
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
            Text("Frames Played: $framesPlayed")
            val roundedLevel = ((averageInputLevel * 1000).toInt() / 1000.0f)
            Text("Average Input Level: $roundedLevel")
            if (permissionStatusMessage.isNotEmpty()) {
                Text("Permission: $permissionStatusMessage")
            }
        }
    }
}
