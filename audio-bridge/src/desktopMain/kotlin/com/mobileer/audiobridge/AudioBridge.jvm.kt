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

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

internal actual fun instantiateAudioOutputBridge(config: AudioConfig): AudioOutputBridge {
    return JavaSoundOutputBridge(config)
}

internal class JavaSoundOutputBridge(private val config: AudioConfig) : AudioOutputBridge {

    private var mLine: SourceDataLine? = null
    private var mSampleRate = config.sampleRate
    private val mChannelCount = config.channels
    private var mFormat: AudioFormat? = null
    private var mByteBuffer: ByteArray? = null
    private val mAudioFramesPerJavaBuffer = 1024

    override fun open(): AudioResult {
        val bytesPerFrame = mChannelCount * 2 // 16 bit
        val bufferSize = mAudioFramesPerJavaBuffer * bytesPerFrame
        mByteBuffer = ByteArray(bufferSize)
        val bitsPerSample = 16
        mFormat = AudioFormat(
            mSampleRate.toFloat(), bitsPerSample,
            mChannelCount, true, false
        )
        val info = DataLine.Info(SourceDataLine::class.java, mFormat)
        if (!AudioSystem.isLineSupported(info)) {
            println("Line not supported $info")
            return AudioResult.ERROR_INVALID_FORMAT
        }
        return try {
            mLine = AudioSystem.getLine(info) as SourceDataLine
            mLine!!.open(mFormat, bufferSize)
            AudioResult.OK
        } catch (e: LineUnavailableException) {
            println("LineUnavailableException $e")
            AudioResult.ERROR_UNAVAILABLE
        }
    }

    override fun start(): AudioResult {
        val line = mLine ?: return AudioResult.ERROR_INVALID_STATE
        line.start()
        return AudioResult.OK
    }

    override fun write(buffer: FloatArray,
                     offsetFrames: Int,
                     numFrames: Int): Int {
        if (mLine == null || !mLine!!.isOpen) {
            return -1
        }
        var framesToWrite = numFrames

        // numFrames is the number of stereo frames. Each frame has 2 samples (left and right).
        // The input buffer contains interleaved samples: L0, R0, L1, R1, ...
        // So, the number of float samples to process is numFrames * 2.
        var numSamplesToProcess = numFrames * mChannelCount

        val byteBuffer = mByteBuffer ?: return -1
        if (numSamplesToProcess * 2 > byteBuffer.size) {
            numSamplesToProcess = byteBuffer.size / 2
            framesToWrite = numSamplesToProcess / 2
        }

        val startSample: Int = offsetFrames * mChannelCount
        val endSample: Int = (offsetFrames + framesToWrite) * mChannelCount
        var byteIndex = 0
        for (sampleIndex in startSample until endSample) {
            val sample = (buffer[sampleIndex] * 32767.0).toInt().toShort()
            byteBuffer[byteIndex++] = sample.toByte()
            byteBuffer[byteIndex++] = (sample.toInt() shr 8).toByte()
        }
        val bytesWritten = mLine!!.write(byteBuffer, 0, byteIndex)
        return bytesWritten / (mChannelCount * 2)
    }

    override fun stop() {
        mLine?.drain()
        mLine?.stop()
    }

    override fun close() {
        stop()
        mLine?.close()
        mLine = null
        mByteBuffer = null
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