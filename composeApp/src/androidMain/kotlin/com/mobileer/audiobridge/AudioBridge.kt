package com.mobileer.audiobridge

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual class AudioBridge actual constructor(private val context: Any?) {

    private var mSampleRate = 44100 // Default sample rate
    private var mChannelCount = 2 // Stereo
    private var mAudioTrack: AudioTrack? = null

    /**
     * Open the stream.
     *
     * @param sampleRate
     */
    actual fun open(context: Any?, sampleRate: Int): Int {
        mSampleRate = sampleRate // We'll stick to the requested sampleRate if possible, otherwise use default
        val minBufferSize = AudioTrack.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferSize < 0) {
            return minBufferSize // Error code from getMinBufferSize
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
            .build()

        return if (mAudioTrack?.state == AudioTrack.STATE_INITIALIZED) 0 else -1 // TODO define result codes
    }

    /**
     * Start the stream.
     */
    actual fun start(): Int {
        mAudioTrack?.play()
        return 0 // TODO define result codes
    }

    /**
     * Write the data to the stream.
     *
     * @param buffer
     * @param offset
     * @param numFrames
     * @return
     */
    actual fun write(buffer: FloatArray, offset: Int, numFrames: Int): Int {
        return mAudioTrack?.write(buffer, offset, numFrames * mChannelCount, AudioTrack.WRITE_BLOCKING)
            ?: -1 // Error writing
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
        // This is a common value, but it can vary.
        // For more accurate value, you might query AudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return mAudioTrack?.bufferSizeInFrames ?: 128
    }
}
