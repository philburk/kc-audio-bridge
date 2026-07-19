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

external fun setAudioPair(framesWritten: Int, left: Float, right: Float): Boolean
external fun getOutputFramesWritten(): Int
external fun getOutputFramesRead(): Int
external fun getOutputFramesPerBurst(): Int
external fun getOutputCapacityInFrames(): Int
external fun setOutputFramesWritten(value: Int)
external fun startWebAudio()
external fun stopWebAudio()
external fun getAudioSampleRate(): Int

internal actual fun instantiateAudioOutputBridge(config: AudioConfig): AudioOutputBridge {
    return WasmAudioOutputBridge(config)
}

internal class WasmAudioOutputBridge(private val config: AudioConfig) : AudioOutputBridge {
    override fun open(): AudioResult {
        startWebAudio()
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
}

internal actual fun instantiateAudioInputBridge(config: AudioConfig): AudioInputBridge {
    return StubAudioInputBridge(config)
}

internal actual fun isAudioInputSupported(): Boolean {
    return false
}

internal class StubAudioInputBridge(private val config: AudioConfig) : AudioInputBridge {
    override fun open(): AudioResult {
        return AudioResult.OK
    }

    override fun start(): AudioResult {
        return AudioResult.OK
    }

    override fun stop() {
    }

    override fun close() {
    }

    override fun getChannelCount(): Int {
        return config.channels
    }

    override fun getFramesPerBurst(): Int {
        return config.framesPerBuffer
    }

    override fun getSampleRate(): Int {
        return config.sampleRate
    }

    override fun read(buffer: FloatArray, offsetFrames: Int, numFrames: Int): Int {
        val start = offsetFrames * config.channels
        val end = (offsetFrames + numFrames) * config.channels
        if (start in buffer.indices && end <= buffer.size) {
            buffer.fill(0.0f, start, end)
        }
        return numFrames
    }
}

