package org.mobileer.audiobridge

actual class AudioBridge actual constructor(context: Any?) {

    private var stream = JavaSoundOutputStream()

    actual fun open(context: Any?, sampleRate: Int): Int {
        val person = Person("Phil")
        stream.open(sampleRate)
        return 0
    }

    actual fun getSampleRate(): Int {
        return stream.getSampleRate()
    }

    actual fun start(): Int {
        return 0
    }

    actual fun getChannelCount(): Int {
        return 2
    } // STEREO

    actual fun getFramesPerBurst(): Int {
        return 128
    }

    /**
     * Write some stereo audio data to the output stream.
     */
    actual fun write(
        buffer: FloatArray,
        offset: Int,
        numFrames: Int
    ): Int {
        return stream.write(buffer, offset, numFrames)
    }

    actual fun stop() {}
    actual fun close() {
        stream.close()
    }
}
