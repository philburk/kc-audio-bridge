package org.mobileer.audiobridge

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

actual class AudioBridge actual constructor(private val context: Any?) {

    private var mLine: SourceDataLine? = null
    private var mSampleRate = 0
    private var mChannelCount = 0
    private var mFormat: AudioFormat? = null
    private var mByteBuffer: ByteArray? = null
    private val mAudioFramesPerJavaBuffer = 1024

    /**
     * Open the stream.
     *
     * @param sampleRate
     * @param channelCount
     */
    actual fun open(context: Any?, sampleRate: Int): Int {
        mSampleRate = sampleRate
        mChannelCount = 2 // TODO pass as parameter
        val bytesPerFrame = mChannelCount * 2 // 16 bit
        val bufferSize = mAudioFramesPerJavaBuffer * bytesPerFrame
        // Allocate a byte array for the native data.
        mByteBuffer = ByteArray(bufferSize)
        val bitsPerSample = 16
        mFormat = AudioFormat(
            sampleRate.toFloat(), bitsPerSample,
            mChannelCount, true, /* signed */
            false
        ) /* littleEndian */
        val info = DataLine.Info(SourceDataLine::class.java, mFormat)
        if (!AudioSystem.isLineSupported(info)) {
            println("Line not supported $info")
            return -1 // TODO define result codes
        }
        return try {
            mLine = AudioSystem.getLine(info) as SourceDataLine
            // When the line is opened, it acquires necessary system resources and becomes operational.
            mLine!!.open(mFormat, bufferSize)
            0
        } catch (e: LineUnavailableException) {
            println("LineUnavailableException $e")
            -2 // TODO define result codes
        }
    }

    /**
     * Start the stream.
     */
    actual fun start(): Int {
        mLine!!.start()
        return 0 // TODO define result codes
    }

    /**
     * Write the data to the stream.
     *
     * @param floatArray
     * @param offset
     * @param numFrames
     * @return
     */
    actual fun write(buffer: FloatArray, offset: Int, numFrames: Int): Int {
        if (mLine == null || !mLine!!.isOpen()) {
            return 0
        }
        var framesToWrite = numFrames;

        // numFrames is the number of stereo frames. Each frame has 2 samples (left and right).
        // The input buffer contains interleaved samples: L0, R0, L1, R1, ...
        // So, the number of float samples to process is numFrames * 2.
        var numSamplesToProcess = numFrames * mChannelCount

        val byteBuffer = mByteBuffer ?: return -1 // Stream not open
        if (numSamplesToProcess * 2 > byteBuffer.size) { // Each sample becomes 2 bytes
            numSamplesToProcess = byteBuffer.size / 2;
            framesToWrite = numSamplesToProcess / 2;
        }

        // Convert float samples to 16-bit PCM then pack into a byte array.
        val startSample: Int = offset * mChannelCount
        val endSample: Int = (offset + framesToWrite) * mChannelCount
        var byteIndex = 0
        for (sampleIndex in startSample until endSample) {
            // Convert floating point to 16-bit short.
            val sample = (buffer[sampleIndex] * 32767.0).toInt().toShort()
            // Write short as 2 bytes, little endian.
            byteBuffer[byteIndex++] = sample.toByte()
            byteBuffer[byteIndex++] = (sample.toInt() shr 8).toByte()
        }
        val bytesWritten = mLine!!.write(byteBuffer, 0, byteIndex)
        return bytesWritten / (mChannelCount * 2) //  bytes per frame
    }

    /**
     * Stop the stream.
     */
    actual fun stop() {
        mLine?.drain()
        mLine?.stop()
    }

    /**
     * Close the stream and release resources.
     */
    actual fun close() {
        stop()
        mLine?.close()
        mLine = null
        mByteBuffer = null
    }

    actual fun getSampleRate(): Int {
        return mSampleRate
    }

    actual fun getChannelCount(): Int {
        return mChannelCount
    }

    actual fun getFramesPerBurst(): Int {
        return 128
    }
}
