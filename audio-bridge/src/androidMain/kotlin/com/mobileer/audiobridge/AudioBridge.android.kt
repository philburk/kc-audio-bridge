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

internal actual fun instantiateAudioOutputBridge(config: AudioConfig): AudioOutputBridge {
    return AudioTrackOutputBridge(config)
}

internal class AudioTrackOutputBridge(private val config: AudioConfig) : AudioOutputBridge {

    private var mSampleRate = config.sampleRate
    private var mChannelCount = config.channels
    private var mAudioTrack: AudioTrack? = null

    override fun open(): AudioResult {
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

    override fun start(): AudioResult {
        val track = mAudioTrack ?: return AudioResult.ERROR_INVALID_STATE
        track.play()
        return AudioResult.OK
    }

    override fun write(buffer: FloatArray,
                     offsetFrames: Int,
                     numFrames: Int): Int {
        val numFloatsOrError =  mAudioTrack?.write(buffer,
            offsetFrames * mChannelCount,
                numFrames * mChannelCount,
                AudioTrack.WRITE_BLOCKING
            ) ?: AudioTrack.ERROR_DEAD_OBJECT
        return if (numFloatsOrError < 0) numFloatsOrError else
                (numFloatsOrError / mChannelCount)
    }

    override fun stop() {
        if (mAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack?.stop()
        }
    }

    override fun close() {
        stop()
        mAudioTrack?.release()
        mAudioTrack = null
    }

    override fun getSampleRate(): Int {
        return mSampleRate
    }

    override fun getChannelCount(): Int {
        return mChannelCount
    }

    override fun getFramesPerBurst(): Int {
        return config.framesPerBuffer
    }
}
