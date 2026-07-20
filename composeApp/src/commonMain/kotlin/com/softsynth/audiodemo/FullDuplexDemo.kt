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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
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
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

enum class DuplexMode {
    IDLE,
    PREPARING,
    STABLE
}

@Composable
fun FullDuplexDemo(
    selectedInputId: Int,
    selectedOutputId: Int,
    isEnabled: Boolean,
    onStateChanged: (isRunning: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = getPlatformContext()
    val audioInputSupported = remember { AudioInputBridge.isSupported() }

    var duplexMode by remember { mutableStateOf(DuplexMode.IDLE) }
    var loopGain by remember { mutableStateOf(0.1f) }

    var framesReadCount by remember { mutableStateOf(0L) }
    var framesWrittenCount by remember { mutableStateOf(0L) }
    var permissionStatusMessage by remember { mutableStateOf("") }
    var preparingLoopsCount by remember { mutableStateOf(0) }

    var activeInputDeviceName by remember { mutableStateOf("None") }
    var activeOutputDeviceName by remember { mutableStateOf("None") }
    var averageInputLevel by remember { mutableStateOf(0.0f) }
    var lastFramesReadPreparing by remember { mutableStateOf(0) }

    var duplexJob by remember { mutableStateOf<Job?>(null) }

    fun stopDuplex() {
        duplexJob?.cancel()
        duplexJob = null
        duplexMode = DuplexMode.IDLE
        activeInputDeviceName = "None"
        activeOutputDeviceName = "None"
        averageInputLevel = 0.0f
        onStateChanged(false)
    }

    fun startDuplex() {
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

            stopDuplex()
            duplexMode = DuplexMode.PREPARING
            framesReadCount = 0L
            framesWrittenCount = 0L
            preparingLoopsCount = 0
            averageInputLevel = 0.0f
            onStateChanged(true)

            val optimalSampleRate = AudioDeviceManager.getOptimalSampleRate()
            val optimalFramesPerBuffer = AudioDeviceManager.getOptimalFramesPerBuffer()

            val inputBridge = AudioInputBridge.create {
                sampleRate = optimalSampleRate
                framesPerBuffer = optimalFramesPerBuffer
                channels = 1
                deviceId = selectedInputId
            }
            val outputBridge = AudioOutputBridge.create {
                sampleRate = optimalSampleRate
                framesPerBuffer = optimalFramesPerBuffer
                deviceId = selectedOutputId
            }

            duplexJob = scope.launch(Dispatchers.Default) {
                val inputOpen = inputBridge.open()
                if (inputOpen != AudioResult.OK) {
                    println("Duplex: Failed to open input: $inputOpen")
                    stopDuplex()
                    return@launch
                }

                val outputOpen = outputBridge.open()
                if (outputOpen != AudioResult.OK) {
                    println("Duplex: Failed to open output: $outputOpen")
                    inputBridge.close()
                    stopDuplex()
                    return@launch
                }

                val inputStart = inputBridge.start()
                if (inputStart != AudioResult.OK) {
                    println("Duplex: Failed to start input: $inputStart")
                    inputBridge.close()
                    outputBridge.close()
                    stopDuplex()
                    return@launch
                }

                val outputStart = outputBridge.start()
                if (outputStart != AudioResult.OK) {
                    println("Duplex: Failed to start output: $outputStart")
                    inputBridge.stop()
                    inputBridge.close()
                    outputBridge.close()
                    stopDuplex()
                    return@launch
                }

                activeInputDeviceName = inputBridge.getCurrentDeviceName()
                activeOutputDeviceName = outputBridge.getCurrentDeviceName()

                val burstSize = outputBridge.getFramesPerBurst()
                val tempInputBuffer = FloatArray(burstSize * 4) // extra buffer headroom
                val tempOutputBuffer = FloatArray(burstSize * 2) // stereo interleaved

                val STABLE_LOOPS_REQUIRED = 8
                var stableLoopsConsecutive = 0

                try {
                    while (isActive) {
                        if (duplexMode == DuplexMode.PREPARING) {
                            // 1. Query all available input data asynchronously (non-blocking) and count it
                            var totalReadThisLoop = 0
                            while (isActive) {
                                val remainingSpace = tempInputBuffer.size - totalReadThisLoop
                                if (remainingSpace <= 0) break
                                val read = inputBridge.read(tempInputBuffer, totalReadThisLoop, min(burstSize, remainingSpace))
                                if (read <= 0) break
                                totalReadThisLoop += read
                            }

                            framesReadCount += totalReadThisLoop
                            lastFramesReadPreparing = totalReadThisLoop

                            // Calculate input level for level monitor
                            var sum = 0.0f
                            for (i in 0 until totalReadThisLoop) {
                                sum += kotlin.math.abs(tempInputBuffer[i])
                            }
                            averageInputLevel = if (totalReadThisLoop > 0) sum / totalReadThisLoop else 0.0f

                            // Discard input and write silence to output
                            tempOutputBuffer.fill(0.0f)
                            
                            // Check if the input rate matches or falls below the output burst size (with 25% tolerance for OS jitter)
                            val limit = burstSize * 5 / 4
                            if (totalReadThisLoop <= limit) {
                                stableLoopsConsecutive++
                                preparingLoopsCount = stableLoopsConsecutive
                                println("Duplex preparing: read $totalReadThisLoop <= limit $limit. Stable loops: $stableLoopsConsecutive / $STABLE_LOOPS_REQUIRED")
                                if (stableLoopsConsecutive >= STABLE_LOOPS_REQUIRED) {
                                    duplexMode = DuplexMode.STABLE
                                    println("Duplex transitioned to STABLE mode.")
                                }
                            } else {
                                println("Duplex preparing backlog draining: read $totalReadThisLoop > limit $limit. Resetting stable loops count.")
                                stableLoopsConsecutive = 0
                                preparingLoopsCount = 0
                            }

                            // Suspend-write silence
                            val written = outputBridge.writeSuspending(tempOutputBuffer, 0, burstSize, timeoutMillis = 1000L)
                            if (written < 0) break
                            framesWrittenCount += written
                        } else {
                            // STABLE Mode
                            // Read asynchronously from input directly into output buffer, up to output burst size.
                            // Since input is mono and output is stereo, we read to tempInputBuffer and interleave.
                            val readCount = inputBridge.read(tempInputBuffer, 0, burstSize)
                            framesReadCount += readCount

                            // Calculate input level for level monitor
                            var sum = 0.0f
                            for (i in 0 until readCount) {
                                sum += kotlin.math.abs(tempInputBuffer[i])
                            }
                            averageInputLevel = if (readCount > 0) sum / readCount else 0.0f

                            val currentGain = loopGain
                            for (i in 0 until readCount) {
                                val sample = tempInputBuffer[i] * currentGain
                                tempOutputBuffer[i * 2] = sample
                                tempOutputBuffer[i * 2 + 1] = sample
                            }

                            // Fill remaining part with zeros
                            for (i in readCount until burstSize) {
                                tempOutputBuffer[i * 2] = 0.0f
                                tempOutputBuffer[i * 2 + 1] = 0.0f
                            }

                            // Write to output using writeSuspending
                            val written = outputBridge.writeSuspending(tempOutputBuffer, 0, burstSize, timeoutMillis = 1000L)
                            if (written < 0) break
                            framesWrittenCount += written
                        }
                    }
                } finally {
                    inputBridge.stop()
                    inputBridge.close()
                    outputBridge.stop()
                    outputBridge.close()
                    stopDuplex()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            duplexJob?.cancel()
        }
    }

    Column {
        Text(
            "Full Duplex Audio Demo",
            style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )

        if (!audioInputSupported) {
            Text("Full Duplex Audio is not supported on this platform.", color = androidx.compose.ui.graphics.Color.Red)
        } else {
            Row {
                Button(
                    onClick = { startDuplex() },
                    enabled = isEnabled && duplexMode == DuplexMode.IDLE
                ) {
                    Text("START DUPLEX")
                }

                Button(
                    onClick = { stopDuplex() },
                    enabled = duplexMode != DuplexMode.IDLE
                ) {
                    Text("STOP")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (duplexMode) {
                DuplexMode.PREPARING -> {
                    Text("Status: Preparing... ($preparingLoopsCount / 8 stable loops)")
                    Text("Last Frames Read (Preparing): $lastFramesReadPreparing")
                }
                DuplexMode.STABLE -> {
                    Text("Status: Stable")
                }
                DuplexMode.IDLE -> {
                    Text("Status: Idle")
                }
            }

            if (duplexMode != DuplexMode.IDLE) {
                Text("Active Input: $activeInputDeviceName")
                Text("Active Output: $activeOutputDeviceName")
            }

            Text("Frames Read: $framesReadCount")
            Text("Frames Written: $framesWrittenCount")
            val roundedLevel = ((averageInputLevel * 1000).toInt() / 1000.0f)
            Text("Average Input Level: $roundedLevel")

            Spacer(modifier = Modifier.height(16.dp))

            // Loop Gain Slider
            Row(modifier = Modifier.width(350.dp)) {
                val roundedGain = ((loopGain * 100).toInt() / 100.0f)
                Text("Loop Gain: $roundedGain", modifier = Modifier.width(120.dp))
                Slider(
                    value = loopGain,
                    onValueChange = { loopGain = it },
                    valueRange = 0.0f..2.0f,
                    modifier = Modifier.weight(1.0f)
                )
            }

            if (permissionStatusMessage.isNotEmpty()) {
                Text("Permission: $permissionStatusMessage")
            }
        }
    }
}
