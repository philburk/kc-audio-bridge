package com.mobileer.audiobridge

actual class AudioBridge {
    actual fun open(sampleRate: Int): AudioResult {
        // TODO - Implement this for iOS
        return AudioResult.OK
    }

    actual fun start(): AudioResult {
        // TODO - Implement this for iOS
        return AudioResult.OK
    }

    actual fun stop() {
        // TODO - Implement this for iOS
    }

    actual fun close() {
        // TODO - Implement this for iOS
    }

    actual fun write(buffer: FloatArray, offsetFrames: Int, numFrames: Int): Int {
        // TODO - Implement this for iOS
        return 0
    }

    actual fun getChannelCount(): Int {
        // TODO - Implement this for iOS
        return 2
    }

    actual fun getFramesPerBurst(): Int {
        // TODO - Implement this for iOS
        return 256
    }

    actual fun getSampleRate(): Int {
        // TODO - Implement this for iOS
        return 48000
    }
}
