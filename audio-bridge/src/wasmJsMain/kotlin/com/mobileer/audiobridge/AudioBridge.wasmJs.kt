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

package com.mobileer.audiobridge

external fun setAudioPair(framesWritten: Int, left: Float, right: Float): Boolean
external fun getOutputFramesWritten(): Int
external fun getOutputFramesRead(): Int
external fun getOutputFramesPerBurst(): Int
external fun getOutputCapacityInFrames(): Int
external fun setOutputFramesWritten(value: Int)
external fun startWebAudio()
external fun stopWebAudio()
external fun getAudioSampleRate(): Int

actual class AudioBridge actual constructor() {
    actual fun open(sampleRate: Int): AudioResult {
        startWebAudio()
        return AudioResult.OK
    }

    actual fun getSampleRate(): Int {
        return getAudioSampleRate()
    }

    actual fun start(): AudioResult {
        return AudioResult.OK
    }

    actual fun getChannelCount(): Int {
        return 2  // STEREO
    }

    actual fun getFramesPerBurst(): Int {
        return getOutputFramesPerBurst()
    }

    /**
     * Write some audio data to the output stream.
     */
    actual fun write(
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

    actual fun stop() {}

    actual fun close() {
        stopWebAudio()
    }
}
