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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation

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

internal actual fun instantiateAudioInputBridge(config: AudioConfig): AudioInputBridge {
    return AudioRecordInputBridge(config)
}

internal actual fun isAudioInputSupported(): Boolean {
    return true
}

internal class AudioRecordInputBridge(private val config: AudioConfig) : AudioInputBridge {

    private var mSampleRate = config.sampleRate
    private var mChannelCount = config.channels
    private var mAudioRecord: AudioRecord? = null

    override fun open(): AudioResult {
        val channelMask = if (mChannelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBufferSize = AudioRecord.getMinBufferSize(
            mSampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferSize < 0) {
            return AudioResult.ERROR_INTERNAL
        }

        try {
            mAudioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(mSampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()
        } catch (e: SecurityException) {
            return AudioResult.ERROR_UNAVAILABLE
        } catch (e: Exception) {
            return AudioResult.ERROR_INTERNAL
        }

        return if (mAudioRecord?.state == AudioRecord.STATE_INITIALIZED) AudioResult.OK else
            AudioResult.ERROR_UNAVAILABLE
    }

    override fun start(): AudioResult {
        val record = mAudioRecord ?: return AudioResult.ERROR_INVALID_STATE
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            return AudioResult.ERROR_INVALID_STATE
        } catch (e: SecurityException) {
            return AudioResult.ERROR_UNAVAILABLE
        }
        return AudioResult.OK
    }

    override fun read(buffer: FloatArray, offsetFrames: Int, numFrames: Int): Int {
        val record = mAudioRecord ?: return -1
        val numSamplesToRead = numFrames * mChannelCount
        val readResult = record.read(
            buffer,
            offsetFrames * mChannelCount,
            numSamplesToRead,
            AudioRecord.READ_BLOCKING
        )

        return if (readResult < 0) {
            -1
        } else {
            readResult / mChannelCount
        }
    }

    override fun stop() {
        try {
            if (mAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecord?.stop()
            }
        } catch (e: Exception) {
            // Ignore state exceptions on stop
        }
    }

    override fun close() {
        stop()
        mAudioRecord?.release()
        mAudioRecord = null
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

object AudioBridgeAndroid {
    private val pendingRequests = mutableMapOf<Int, CancellableContinuation<AudioPermissionState>>()
    private var nextRequestCode = 1000

    internal fun registerRequest(continuation: CancellableContinuation<AudioPermissionState>): Int {
        val code = nextRequestCode++
        pendingRequests[code] = continuation
        return code
    }

    /**
     * Call this from your Activity's onRequestPermissionsResult to forward the result to the coroutine.
     */
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val continuation = pendingRequests.remove(requestCode) ?: return
        val recordAudioIndex = permissions.indexOf(android.Manifest.permission.RECORD_AUDIO)
        if (recordAudioIndex != -1 && grantResults.getOrNull(recordAudioIndex) == PackageManager.PERMISSION_GRANTED) {
            continuation.resume(AudioPermissionState.GRANTED)
        } else {
            continuation.resume(AudioPermissionState.DENIED)
        }
    }
}

internal actual fun getAudioPermissionState(context: Any?): AudioPermissionState {
    val ctx = context as? Context ?: return AudioPermissionState.UNDETERMINED
    val hasPermission = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    return if (hasPermission) {
        AudioPermissionState.GRANTED
    } else {
        val activity = ctx as? Activity
        if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)) {
            AudioPermissionState.DENIED
        } else {
            AudioPermissionState.UNDETERMINED
        }
    }
}

internal actual suspend fun requestAudioPermission(context: Any?): AudioPermissionState {
    val activity = context as? Activity ?: return AudioPermissionState.DENIED
    if (getAudioPermissionState(activity) == AudioPermissionState.GRANTED) {
        return AudioPermissionState.GRANTED
    }

    return suspendCancellableCoroutine { continuation ->
        val requestCode = AudioBridgeAndroid.registerRequest(continuation)
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            requestCode
        )
        continuation.invokeOnCancellation {
            // clean up
        }
    }
}

