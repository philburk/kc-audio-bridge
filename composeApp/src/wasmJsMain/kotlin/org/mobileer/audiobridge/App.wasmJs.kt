package org.mobileer.audiobridge

external fun setAudioPair(framesWritten: Int, left: Float, right: Float): Boolean
external fun getOutputFramesWritten(): Int
external fun getOutputFramesRead(): Int
external fun getOutputFramesPerBurst(): Int
external fun setOutputFramesWritten(value: Int)

actual class AudioBridge actual constructor(context: Any?) {
    actual fun open(context: Any?, sampleRate: Int): Int { return 0 }
    actual fun start(): Int { return 0 }

    actual fun getChannelCount(): Int { return 2 } // STEREO
    actual fun getFramesPerBurst(): Int { return getOutputFramesPerBurst() }
    /**
     * Write a buffer of audio data to the output stream.
     */
    actual fun write(
        buffer: FloatArray,
        offset: Int,
        numFrames: Int
    ): Int {
        val framesWritten = getOutputFramesWritten()
        val framesRead = getOutputFramesRead()
        var frameIndex = offset
        var frameCount = 0
        while ((frameCount < numFrames) // data left to write
                &&  (framesRead > (framesWritten + frameCount))) { // is there room in the queue?
                setAudioPair(framesWritten + frameIndex,
                    buffer[frameIndex * 2],
                    buffer[frameIndex * 2 + 1])
                frameIndex += 1
                frameCount += 1
        }
        setOutputFramesWritten(framesWritten + frameCount)
        return frameIndex
    }
    actual fun stop() {}
    actual fun close() {}
}
