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

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.Mixer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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
    private var mDeviceName = "Default Output"

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
        val mixer = getMixerForDevice(config.deviceId, SourceDataLine::class.java)
        return try {
            mLine = if (mixer != null) {
                mDeviceName = mixer.mixerInfo.name
                mixer.getLine(info) as SourceDataLine
            } else {
                val defaultMixer = try { javax.sound.sampled.AudioSystem.getMixer(null) } catch (e: Exception) { null }
                val fallbackName = defaultMixer?.mixerInfo?.name ?: "Default Output"
                mDeviceName = if (System.getProperty("os.name").lowercase().contains("mac")) {
                    getDefaultDeviceNameMac(isInput = false) ?: fallbackName
                } else {
                    fallbackName
                }
                AudioSystem.getLine(info) as SourceDataLine
            }
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

    override fun getCurrentDeviceName(): String {
        return mDeviceName
    }
}

internal actual fun instantiateAudioInputBridge(config: AudioConfig): AudioInputBridge {
    return JavaSoundInputBridge(config)
}

internal actual fun isAudioInputSupported(): Boolean {
    return true
}

internal actual fun getAudioPermissionState(context: Any?): AudioPermissionState {
    return AudioPermissionState.GRANTED
}

internal actual suspend fun requestAudioPermission(context: Any?): AudioPermissionState {
    return AudioPermissionState.GRANTED
}

internal actual fun getOutputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> {
    return getJavaSoundDevicesFlow(isInput = false)
}

internal actual fun getInputDevicesFlow(): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> {
    return getJavaSoundDevicesFlow(isInput = true)
}

internal class JavaSoundInputBridge(private val config: AudioConfig) : AudioInputBridge {

    private var mLine: TargetDataLine? = null
    private var mSampleRate = config.sampleRate
    private val mChannelCount = config.channels
    private var mFormat: AudioFormat? = null
    private var mByteBuffer: ByteArray? = null
    private val mAudioFramesPerJavaBuffer = 1024
    private var mDeviceName = "Default Input"

    override fun open(): AudioResult {
        val bytesPerFrame = mChannelCount * 2 // 16 bit
        val bufferSize = mAudioFramesPerJavaBuffer * bytesPerFrame
        mByteBuffer = ByteArray(bufferSize)
        val bitsPerSample = 16
        mFormat = AudioFormat(
            mSampleRate.toFloat(), bitsPerSample,
            mChannelCount, true, false
        )
        val info = DataLine.Info(TargetDataLine::class.java, mFormat)
        val mixer = getMixerForDevice(config.deviceId, TargetDataLine::class.java)
        return try {
            mLine = if (mixer != null) {
                mDeviceName = mixer.mixerInfo.name
                mixer.getLine(info) as TargetDataLine
            } else {
                val defaultMixer = try { javax.sound.sampled.AudioSystem.getMixer(null) } catch (e: Exception) { null }
                val fallbackName = defaultMixer?.mixerInfo?.name ?: "Default Input"
                mDeviceName = if (System.getProperty("os.name").lowercase().contains("mac")) {
                    getDefaultDeviceNameMac(isInput = true) ?: fallbackName
                } else {
                    fallbackName
                }
                AudioSystem.getLine(info) as TargetDataLine
            }
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

    override fun read(buffer: FloatArray,
                     offsetFrames: Int,
                     numFrames: Int): Int {
        val line = mLine ?: return -1
        if (!line.isOpen) {
            return -1
        }
        var framesToRead = numFrames
        var numSamplesToProcess = numFrames * mChannelCount

        val byteBuffer = mByteBuffer ?: return -1
        if (numSamplesToProcess * 2 > byteBuffer.size) {
            numSamplesToProcess = byteBuffer.size / 2
            framesToRead = numSamplesToProcess / mChannelCount
        }

        val bytesRead = line.read(byteBuffer, 0, framesToRead * mChannelCount * 2)
        if (bytesRead < 0) {
            return -1
        }

        val samplesRead = bytesRead / 2
        val startSample = offsetFrames * mChannelCount
        var byteIndex = 0
        for (i in 0 until samplesRead) {
            val low = byteBuffer[byteIndex++].toInt() and 0xFF
            val high = byteBuffer[byteIndex++].toInt()
            val sampleVal = ((high shl 8) or low).toShort()
            buffer[startSample + i] = sampleVal / 32768.0f
        }

        return samplesRead / mChannelCount
    }

    override fun stop() {
        mLine?.stop()
        mLine?.flush()
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

    override fun getCurrentDeviceName(): String {
        return mDeviceName
    }
}

private fun getMixerForDevice(deviceId: Int, lineClass: Class<*>): Mixer? {
    if (deviceId == -1) {
        return null
    }
    val mixers = AudioSystem.getMixerInfo()
    for (info in mixers) {
        val hash = (info.name + info.vendor + info.description).hashCode()
        if (hash == deviceId) {
            val mixer = AudioSystem.getMixer(info)
            if (mixer.isLineSupported(DataLine.Info(lineClass, null))) {
                return mixer
            }
        }
    }
    return null
}

private fun getJavaSoundDevices(isInput: Boolean): List<AudioDeviceInfo> {
    val mixers = AudioSystem.getMixerInfo()
    val lineClass = if (isInput) TargetDataLine::class.java else SourceDataLine::class.java
    val list = mutableListOf<AudioDeviceInfo>()
    for (info in mixers) {
        if (info.name == "Default Audio Device" || info.name == "Port Default Audio Device") {
            continue
        }
        val mixer = try {
            AudioSystem.getMixer(info)
        } catch (e: Exception) {
            continue
        }
        val lineInfo = DataLine.Info(lineClass, null)
        if (mixer.isLineSupported(lineInfo)) {
            val hash = (info.name + info.vendor + info.description).hashCode()
            list.add(
                AudioDeviceInfo(
                    id = hash,
                    name = info.name.takeIf { it.isNotEmpty() } ?: "Mixer ${hash}",
                    maxChannels = 2,
                    isDefault = false
                )
            )
        }
    }
    return list
}

private fun getJavaSoundDevicesFlow(isInput: Boolean): kotlinx.coroutines.flow.Flow<List<AudioDeviceInfo>> = kotlinx.coroutines.flow.flow {
    var previous = emptyList<AudioDeviceInfo>()
    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
        val current = getJavaSoundDevices(isInput)
        if (current != previous) {
            previous = current
            emit(current)
        }
        delay(2000)
    }
}

private fun getDefaultDeviceNameMac(isInput: Boolean): String? {
    return try {
        val process = ProcessBuilder("system_profiler", "SPAudioDataType").start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val lines = output.lines()
        var currentDevice: String? = null
        val targetKey = if (isInput) "Default Input Device: Yes" else "Default Output Device: Yes"
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.endsWith(":")) {
                val name = trimmed.removeSuffix(":").trim()
                if (name.isNotEmpty() && name != "Devices" && name != "Audio") {
                    currentDevice = name
                }
            } else if (trimmed == targetKey) {
                return currentDevice
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}