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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioDeviceManager

@Composable
fun App() {
    val audioInputSupported = remember { AudioInputBridge.isSupported() }

    // Dynamic Device routing flows
    val outputDevices by AudioDeviceManager.outputDevices.collectAsState(initial = emptyList())
    val inputDevices by AudioDeviceManager.inputDevices.collectAsState(initial = emptyList())
    var selectedOutputId by remember { mutableStateOf(-1) }
    var selectedInputId by remember { mutableStateOf(-1) }

    var outputMenuExpanded by remember { mutableStateOf(false) }
    var inputMenuExpanded by remember { mutableStateOf(false) }

    // Global run states to coordinate UI interaction across demos
    var isSineWaveRunning by remember { mutableStateOf(false) }
    var isRecordPlayRunning by remember { mutableStateOf(false) }
    var isDuplexRunning by remember { mutableStateOf(false) }
    val isAnyRunning = isSineWaveRunning || isRecordPlayRunning || isDuplexRunning

    Column {
        Text("Test AudioBridge on platform ${getPlatform().name}")

        Spacer(modifier = Modifier.height(16.dp))

        // Device Selection Section
        Text(
            "Device Routing Configurations:",
            style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
        
        Row {
            Column {
                Text("Output Device:")
                Box {
                    Button(
                        onClick = { outputMenuExpanded = true },
                        enabled = !isAnyRunning
                    ) {
                        val selectedName = if (selectedOutputId == -1) {
                            "Default Output Device"
                        } else {
                            outputDevices.find { it.id == selectedOutputId }?.name ?: "Unknown Device"
                        }
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
                                text = {
                                    val displayName = buildString {
                                        append(device.name)
                                        if (device.isDefault) {
                                            append(" (default)")
                                        }
                                        append(", max=${device.maxChannels}")
                                    }
                                    Text(displayName)
                                },
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
                            enabled = !isAnyRunning
                        ) {
                            val selectedName = if (selectedInputId == -1) {
                                "Default Input Device"
                            } else {
                                inputDevices.find { it.id == selectedInputId }?.name ?: "Unknown Device"
                            }
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
                                    text = {
                                        val displayName = buildString {
                                            append(device.name)
                                            if (device.isDefault) {
                                                append(" (default)")
                                            }
                                            append(", max=${device.maxChannels}")
                                        }
                                        Text(displayName)
                                    },
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

        // 1. Sine Wave Tone Output Demo Component
        SineWaveDemo(
            selectedOutputId = selectedOutputId,
            isEnabled = !isAnyRunning || isSineWaveRunning,
            onPlayingChanged = { isSineWaveRunning = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Input Recording & Playback Demo Component
        RecordPlayDemo(
            selectedInputId = selectedInputId,
            selectedOutputId = selectedOutputId,
            isEnabled = !isAnyRunning || isRecordPlayRunning,
            onStateChanged = { isRecordPlayRunning = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Full Duplex Audio Demo Component
        FullDuplexDemo(
            selectedInputId = selectedInputId,
            selectedOutputId = selectedOutputId,
            isEnabled = !isAnyRunning || isDuplexRunning,
            onStateChanged = { isDuplexRunning = it }
        )
    }
}
