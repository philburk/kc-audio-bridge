package com.softsynth.audiodemo

import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.readSuspending
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun testAudioInputBridgeSupportedQuery() {
        assertFalse(AudioInputBridge.isSupported(), "Audio input should initially be unsupported on all platforms")
    }

    @Test
    fun testAudioInputBridgeStubLifecycle() {
        val inputBridge = AudioInputBridge.create {
            sampleRate = 48000
            channels = 1
            framesPerBuffer = 128
        }

        assertEquals(1, inputBridge.getChannelCount())
        assertEquals(48000, inputBridge.getSampleRate())
        assertEquals(128, inputBridge.getFramesPerBurst())

        assertEquals(AudioResult.OK, inputBridge.open())
        assertEquals(AudioResult.OK, inputBridge.start())

        val buffer = FloatArray(256) { 1.0f }
        val readFrames = inputBridge.read(buffer, 0, 128)
        assertEquals(128, readFrames)
        // Since it's a stub, the read frames should be zeroed out
        for (i in 0 until 128) {
            assertEquals(0.0f, buffer[i], "Stub buffer should be zeroed out")
        }
        // Beyond read frames should remain untouched
        for (i in 128 until 256) {
            assertEquals(1.0f, buffer[i])
        }

        inputBridge.stop()
        inputBridge.close()
    }

    @Test
    fun testAudioInputBridgeReadSuspending() = runTest {
        val inputBridge = AudioInputBridge.create {
            sampleRate = 48000
            channels = 1
            framesPerBuffer = 128
        }
        assertEquals(AudioResult.OK, inputBridge.open())
        val buffer = FloatArray(256) { 1.0f }
        // Non-blocking read (timeoutMillis = 0L)
        val readFrames = inputBridge.readSuspending(buffer, 0, 128, 0L)
        assertEquals(128, readFrames)
        for (i in 0 until 128) {
            assertEquals(0.0f, buffer[i])
        }
        inputBridge.close()
    }
}