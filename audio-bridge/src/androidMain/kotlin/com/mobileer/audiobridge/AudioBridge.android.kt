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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual class AudioBridge actual constructor() {

    private var mSampleRate = 44100 // Default sample rate
    private var mChannelCount = 2 // Stereo
    private var mAudioTrack: AudioTrack? = null

    /**
     * Open the stream.
     *
     * @param sampleRate
     */
    actual fun open(sampleRate: Int): AudioResult {
        mSampleRate = sampleRate // We'll stick to the requested sampleRate if possible, otherwise use default
        val minBufferSize = AudioTrack.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferSize < 0) {
            return AudioResult.ERROR_INTERNAL
        }

        mAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(mSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        return if (mAudioTrack?.state == AudioTrack.STATE_INITIALIZED) AudioResult.OK else
            AudioResult.ERROR_UNAVAILABLE
    }

    /**
     * Start the stream.
     */
    actual fun start(): AudioResult {
        val track = mAudioTrack
        if (track == null) {
            return AudioResult.ERROR_INVALID_STATE
        }
        track.play()
        return AudioResult.OK
    }

    /**
     * Write the data to the stream.
     *
     * @param buffer
     * @param offset
     * @param numFrames
     * @return number of frames or -1 if an error occurs
     */
    actual fun write(buffer: FloatArray,
                     offsetFrames: Int,
                     numFrames: Int): Int {
        val numFloatsOrError =  mAudioTrack?.write(buffer,
            offsetFrames * mChannelCount,
                numFrames * mChannelCount,
                AudioTrack.WRITE_BLOCKING
            )?: AudioTrack.ERROR_DEAD_OBJECT
        return if (numFloatsOrError < 0) numFloatsOrError else
                (numFloatsOrError / mChannelCount)
    }

    /**
     * Stop the stream.
     */
    actual fun stop() {
        if (mAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack?.stop()
        }
    }

    /**
     * Close the stream and release resources.
     */
    actual fun close() {
        stop()
        mAudioTrack?.release()
        mAudioTrack = null
    }

    actual fun getSampleRate(): Int {
        return mSampleRate
    }

    actual fun getChannelCount(): Int {
        return mChannelCount
    }

    actual fun getFramesPerBurst(): Int {
        return 256; // It is not possible to get an accurate burst size from the Java API.
    }
}
