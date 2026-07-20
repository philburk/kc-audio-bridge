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

package com.softsynth.audiobridge

import kotlin.js.Promise
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

external fun setAudioPair(framesWritten: Int, left: Float, right: Float): Boolean
external fun getOutputFramesWritten(): Int
external fun getOutputFramesRead(): Int
external fun getOutputFramesPerBurst(): Int
external fun getOutputCapacityInFrames(): Int
external fun setOutputFramesWritten(value: Int)
external fun startWebAudio(deviceIdHash: Int)
external fun stopWebAudio()
external fun getAudioSampleRate(): Int

external fun startWebAudioInput(deviceIdHash: Int): Int
external fun stopWebAudioInput()
external fun getInputFramesWritten(): Int
external fun getInputFramesRead(): Int
external fun getInputFramesPerBurst(): Int
external fun getInputCapacityInFrames(): Int
external fun setInputFramesRead(value: Int)
external fun getAudioInputSample(framesRead: Int, channel: Int): Float

@JsFun("() => window.getWasmAudioPermissionState()")
external fun getWasmAudioPermissionState(): JsString

@JsFun("() => window.requestWasmAudioPermission()")
external fun requestWasmAudioPermission(): Promise<JsString>

@JsFun("() => window.getWasmCurrentOutputDeviceName()")
external fun getWasmCurrentOutputDeviceName(): JsString

@JsFun("() => window.getWasmCurrentInputDeviceName()")
external fun getWasmCurrentInputDeviceName(): JsString

internal actual fun instantiateAudioOutputBridge(config: AudioConfig): AudioOutputBridge {
    return WasmAudioOutputBridge(config)
}

internal class WasmAudioOutputBridge(private val config: AudioConfig) : AudioOutputBridge {
    override fun open(): AudioResult {
        startWebAudio(config.deviceId)
        return AudioResult.OK
    }

    override fun getSampleRate(): Int {
        return getAudioSampleRate()
    }

    override fun start(): AudioResult {
        return AudioResult.OK
    }

    override fun getChannelCount(): Int {
        return 2  // STEREO
    }

    override fun getFramesPerBurst(): Int {
        return getOutputFramesPerBurst()
    }

    override fun write(
        buffer: FloatArray,
        offsetFrames: Int,
        numFrames: Int
    ): Int {
        val framesWritten = getOutputFramesWritten()
        val framesRead = getOutputFramesRead()
        val capacity = getOutputCapacityInFrames()
        val emptyFrames = maxOf(0, capacity - (framesWritten - framesRead))
        val framesToWrite = minOf(numFrames, emptyFrames) // data left to write
        for (frameCount in 0 until framesToWrite) {
            val frameIndex = offsetFrames + frameCount
            setAudioPair(
                framesWritten + frameCount,
                buffer[frameIndex * 2], // left channel
                buffer[frameIndex * 2 + 1] // right channel
            )
        }
        // Advance FIFO pointer.
        setOutputFramesWritten(framesWritten + framesToWrite)
        return framesToWrite
    }

    override fun stop() {}

    override fun close() {
        stopWebAudio()
    }

    override fun getCurrentDeviceName(): String {
        return getWasmCurrentOutputDeviceName().toString()
    }
}

internal actual fun instantiateAudioInputBridge(config: AudioConfig): AudioInputBridge {
    return WasmAudioInputBridge(config)
}

internal actual fun isAudioInputSupported(): Boolean {
    return true
}

internal actual fun getAudioPermissionState(context: Any?): AudioPermissionState {
    val state = getWasmAudioPermissionState().toString()
    return when (state) {
        "granted" -> AudioPermissionState.GRANTED
        "denied" -> AudioPermissionState.DENIED
        else -> AudioPermissionState.UNDETERMINED
    }
}

internal actual suspend fun requestAudioPermission(context: Any?): AudioPermissionState {
    return try {
        val state = requestWasmAudioPermission().await<JsString>().toString()
        when (state) {
            "granted" -> AudioPermissionState.GRANTED
            "denied" -> AudioPermissionState.DENIED
            else -> AudioPermissionState.UNDETERMINED
        }
    } catch (e: Throwable) {
        AudioPermissionState.DENIED
    }
}

@JsFun("(kind) => window.getWasmDevicesCount(kind)")
external fun getWasmDevicesCount(kind: JsString): Int

@JsFun("(kind, index) => window.getWasmDeviceName(kind, index)")
external fun getWasmDeviceName(kind: JsString, index: Int): JsString

@JsFun("(kind, index) => window.getWasmDeviceId(kind, index)")
external fun getWasmDeviceId(kind: JsString, index: Int): JsString

private fun queryWasmDevices(isInput: Boolean): List<AudioDeviceInfo> {
    val kind = if (isInput) "audioinput".toJsString() else "audiooutput".toJsString()
    val count = getWasmDevicesCount(kind)
    val list = mutableListOf<AudioDeviceInfo>()
    for (i in 0 until count) {
        val name = getWasmDeviceName(kind, i).toString()
        val idStr = getWasmDeviceId(kind, i).toString()
        val idHash = idStr.hashCode()
        list.add(
            AudioDeviceInfo(
                id = idHash,
                name = name.takeIf { it.isNotEmpty() } ?: (if (isInput) "Microphone $i" else "Speaker $i"),
                maxChannels = 2,
                isDefault = (idStr == "default")
            )
        )
    }
    return list
}

private fun getWasmDevicesFlow(isInput: Boolean): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> = kotlinx.coroutines.flow.flow {
    var previous = emptyList<AudioDeviceInfo>()
    while (currentCoroutineContext().isActive) {
        val current = queryWasmDevices(isInput)
        if (current != previous) {
            previous = current
            emit(current)
        }
        delay(2000)
    }
}

internal actual fun getOutputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> {
    return getWasmDevicesFlow(isInput = false)
}

internal actual fun getInputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> {
    return getWasmDevicesFlow(isInput = true)
}

internal class WasmAudioInputBridge(private val config: AudioConfig) : AudioInputBridge {
    override fun open(): AudioResult {
        val result = startWebAudioInput(config.deviceId)
        return if (result == 0) AudioResult.OK else AudioResult.ERROR_UNAVAILABLE
    }

    override fun start(): AudioResult {
        return AudioResult.OK
    }

    override fun stop() {
        stopWebAudioInput()
    }

    override fun close() {
        stopWebAudioInput()
    }

    override fun getChannelCount(): Int {
        return 1
    }

    override fun getFramesPerBurst(): Int {
        return getInputFramesPerBurst()
    }

    override fun getSampleRate(): Int {
        return getAudioSampleRate()
    }

    override fun getCurrentDeviceName(): String {
        return getWasmCurrentInputDeviceName().toString()
    }

    override fun read(buffer: FloatArray, offsetFrames: Int, numFrames: Int): Int {
        val framesWritten = getInputFramesWritten()
        val framesRead = getInputFramesRead()
        val availableFrames = (framesWritten - framesRead) and 0xFFFFFFFF.toInt()
        val framesToRead = minOf(numFrames, availableFrames)

        for (frameCount in 0 until framesToRead) {
            val frameIndex = offsetFrames + frameCount
            buffer[frameIndex] = getAudioInputSample(framesRead + frameCount, 0)
        }

        setInputFramesRead(framesRead + framesToRead)
        return framesToRead
    }
}

@JsFun("() => { try { const ctx = new (window.AudioContext || window.webkitAudioContext)(); const rate = ctx.sampleRate; ctx.close(); return rate; } catch (e) { return 48000; } }")
external fun getWasmOptimalSampleRate(): Int

internal actual fun getOptimalFramesPerBufferPlatform(): Int {
    return 128
}

internal actual fun getOptimalSampleRatePlatform(): Int {
    return getWasmOptimalSampleRate()
}

