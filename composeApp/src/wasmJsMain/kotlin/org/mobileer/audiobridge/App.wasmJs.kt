package org.mobileer.audiobridge

external fun setAudioPair(framesWritten: Int, left: Float, right: Float): Boolean
external fun getOutputFramesWritten(): Int
external fun getOutputFramesRead(): Int
external fun getOutputFramesPerBurst(): Int
external fun getOutputCapacityInFrames(): Int
external fun setOutputFramesWritten(value: Int)
external fun startWebAudio()
external fun stopWebAudio()

actual class AudioBridge actual constructor(context: Any?) {
    actual fun open(context: Any?, sampleRate: Int): Int {
        startWebAudio()
        return 0
    }
    actual fun start(): Int { return 0 }

    actual fun getChannelCount(): Int { return 2 } // STEREO
    actual fun getFramesPerBurst(): Int { return getOutputFramesPerBurst() }
    /**
     * Write some stereo audio data to the output stream.
     */
    actual fun write(
        buffer: FloatArray,
        offset: Int,
        numFrames: Int
    ): Int {
        val framesWritten = getOutputFramesWritten()
        val framesRead = getOutputFramesRead()
        val capacity = getOutputCapacityInFrames()
        val emptyFrames = maxOf(0, capacity - (framesWritten - framesRead))
        val framesToWrite = minOf(numFrames, emptyFrames) // data left to write
        for (frameCount in 0 until framesToWrite) {
            val frameIndex = offset + frameCount
            setAudioPair(framesWritten + frameCount,
                buffer[frameIndex * 2], // left channel
                buffer[frameIndex * 2 + 1]) // right channel
        }
        setOutputFramesWritten(framesWritten + framesToWrite)
        return framesToWrite
    }
    actual fun stop() {}
    actual fun close() {
        stopWebAudio()
    }
}
