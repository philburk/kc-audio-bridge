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

enum class AudioResult(val code: Int) {
    OK(0),
    ERROR_INVALID_FORMAT(-100),
    ERROR_UNAVAILABLE(-101),
    ERROR_INVALID_STATE(-102),
    ERROR_INTERNAL(-103),
}

expect class AudioBridge() {
    /**
     * Open a stereo audio output stream with float format.
     * Allocate the audio resources.
     * @param sampleRate The requested sample rate in Hz. Actual rate may differ.
     */
    fun open(sampleRate: Int = 44100): AudioResult
    fun start(): AudioResult
    fun stop()
    fun close()

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
     * This may be different than the value passed to open().
     * This is valid after calling open().
     */
    fun getSampleRate(): Int

}
