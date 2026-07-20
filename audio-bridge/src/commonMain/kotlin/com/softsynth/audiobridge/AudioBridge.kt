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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource

enum class AudioResult(val code: Int) {
    OK(0),
    ERROR_INVALID_FORMAT(-100),
    ERROR_UNAVAILABLE(-101),
    ERROR_INVALID_STATE(-102),
    ERROR_INTERNAL(-103),
}

enum class AudioPermissionState {
    GRANTED,
    DENIED,
    UNDETERMINED,
    NOT_SUPPORTED
}

data class AudioDeviceInfo(
    val id: Int,
    val name: String,
    val maxChannels: Int,
    val isDefault: Boolean
)

class AudioConfig internal constructor(
    val sampleRate: Int,
    val channels: Int,
    val framesPerBuffer: Int,
    val deviceId: Int
) {
    class Builder {
        var sampleRate: Int = 44100
        var channels: Int = 2
        var framesPerBuffer: Int = 256
        var deviceId: Int = -1

        internal fun build(): AudioConfig {
            return AudioConfig(sampleRate, channels, framesPerBuffer, deviceId)
        }
    }
}

interface AudioBridge {
    /**
     * Open an audio stream using the configuration when created.
     * Allocate the audio resources.
     * @return AudioResult.OK if successful.
     */
    fun open(): AudioResult
    fun start(): AudioResult
    fun stop()
    fun close()

    /**
     * Get the number of samples in one frame.
     * This is valid after calling open().
     */
    fun getChannelCount(): Int

    /**
     * Get the internal size for one block of audio data that is typically
     * transferred at one time.
     * This is valid after calling open().
     */
    fun getFramesPerBurst(): Int

    /**
     * Get the number of frames transferred per second.
     * This may be different from the requested rate.
     * This is valid after calling open().
     */
    fun getSampleRate(): Int

    /**
     * Get the name of the device currently in use by this stream.
     * This is valid after calling open().
     */
    fun getCurrentDeviceName(): String
}

interface AudioOutputBridge : AudioBridge {
    /**
     * Write some audio data to the output stream.
     * @param buffer The audio data to write.
     * @param offsetFrames The frame offset in the buffer for valid data.
     * @param numFrames The number of frames to write.
     * @return The number of frames actually written or -1 if an error occurs.
     */
    fun write(buffer: FloatArray,
              offsetFrames: Int,
              numFrames: Int): Int

    companion object {
        /**
         * Public factory method to create a platform-specific AudioOutputBridge.
         * Uses a DSL for safe, backward-compatible configuration.
         */
        fun create(configure: AudioConfig.Builder.() -> Unit = {}): AudioOutputBridge {
            val builder = AudioConfig.Builder()
            builder.configure()
            return instantiateAudioOutputBridge(builder.build())
        }
    }
}

interface AudioInputBridge : AudioBridge {
    /**
     * Read some audio data from the input stream.
     * @param buffer The audio data buffer to read into.
     * @param offsetFrames The frame offset in the buffer for writing valid data.
     * @param numFrames The number of frames to read.
     * @return The number of frames actually read or -1 if an error occurs.
     */
    fun read(buffer: FloatArray,
             offsetFrames: Int,
             numFrames: Int): Int

    companion object {
        /**
         * Public factory method to create a platform-specific AudioInputBridge.
         * Uses a DSL for safe, backward-compatible configuration.
         */
        fun create(configure: AudioConfig.Builder.() -> Unit = {}): AudioInputBridge {
            val builder = AudioConfig.Builder()
            builder.configure()
            return instantiateAudioInputBridge(builder.build())
        }

        /**
         * Query whether audio input is supported on the current platform.
         */
        fun isSupported(): Boolean {
            return isAudioInputSupported()
        }

        /**
         * Check the current state of audio input permission.
         * This check is synchronous and non-blocking.
         */
        fun getPermissionState(context: Any? = null): AudioPermissionState {
            return getAudioPermissionState(context)
        }

        /**
         * Request audio input permission from the user.
         * This is a suspending function as prompting the user is asynchronous.
         */
        suspend fun requestPermission(context: Any? = null): AudioPermissionState {
            return requestAudioPermission(context)
        }
    }
}

internal expect fun instantiateAudioOutputBridge(config: AudioConfig): AudioOutputBridge
internal expect fun instantiateAudioInputBridge(config: AudioConfig): AudioInputBridge
internal expect fun isAudioInputSupported(): Boolean
internal expect fun getAudioPermissionState(context: Any?): AudioPermissionState
internal expect suspend fun requestAudioPermission(context: Any?): AudioPermissionState
internal expect fun getOutputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>>
internal expect fun getInputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>>

object AudioDeviceManager {
    val outputDevices: kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> = getOutputDevicesFlow()
    val inputDevices: kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> = getInputDevicesFlow()
}

/**
 * Suspending extension function for AudioOutputBridge.write().
 * This provides a coroutine-friendly way to perform blocking writes without tying up an OS thread.
 *
 * @param buffer The audio data to write.
 * @param offsetFrames The frame offset in the buffer.
 * @param numFrames The number of frames to write.
 * @param timeoutMillis The maximum time to wait for the write to complete. Default is 0 (non-blocking).
 * @return The number of frames actually written, or a negative error code.
 */
suspend fun AudioOutputBridge.writeSuspending(
    buffer: FloatArray,
    offsetFrames: Int,
    numFrames: Int,
    timeoutMillis: Long = 0L
): Int {
    if (timeoutMillis == 0L) {
        return write(buffer, offsetFrames, numFrames)
    }

    val startTime = TimeSource.Monotonic.markNow()
    var framesLeft = numFrames
    var offset = offsetFrames
    val sampleRate = getSampleRate()
    val burstDelayMs = maxOf(1L, 1000L * getFramesPerBurst() / sampleRate)

    while (framesLeft > 0 && currentCoroutineContext().isActive) {
        val written = write(buffer, offset, framesLeft)
        if (written < 0) return written

        offset += written
        framesLeft -= written

        if (framesLeft > 0) {
            if (startTime.elapsedNow().inWholeMilliseconds >= timeoutMillis) break
            delay(burstDelayMs)
        }
    }
    return numFrames - framesLeft
}

/**
 * Suspending extension function for AudioInputBridge.read().
 * This provides a coroutine-friendly way to perform blocking reads without tying up an OS thread.
 *
 * @param buffer The audio data buffer to read into.
 * @param offsetFrames The frame offset in the buffer.
 * @param numFrames The number of frames to read.
 * @param timeoutMillis The maximum time to wait for the read to complete. Default is 0 (non-blocking).
 * @return The number of frames actually read, or a negative error code.
 */
suspend fun AudioInputBridge.readSuspending(
    buffer: FloatArray,
    offsetFrames: Int,
    numFrames: Int,
    timeoutMillis: Long = 0L
): Int {
    if (timeoutMillis == 0L) {
        return read(buffer, offsetFrames, numFrames)
    }

    val startTime = TimeSource.Monotonic.markNow()
    var framesLeft = numFrames
    var offset = offsetFrames
    val sampleRate = getSampleRate()
    val burstDelayMs = maxOf(1L, 1000L * getFramesPerBurst() / sampleRate)

    while (framesLeft > 0 && currentCoroutineContext().isActive) {
        val read = read(buffer, offset, framesLeft)
        if (read < 0) return read

        offset += read
        framesLeft -= read

        if (framesLeft > 0) {
            if (startTime.elapsedNow().inWholeMilliseconds >= timeoutMillis) break
            delay(burstDelayMs)
        }
    }
    return numFrames - framesLeft
}

