package org.mobileer.audiobridge

external fun writeAudioSample(value: Float): Boolean

actual class AudioBridge actual constructor(context: Any?) {
    actual fun write(buffer: FloatArray, numFrames: Int) {
        for (i in 0 until numFrames) {
            if (!writeAudioSample(buffer[i])) {
                break
            }
        }
    }
}
