package org.mobileer.audiobridge


expect class AudioBridge(context: Any? = null) {
    fun open(context: Any? = null,
             sampleRate: Int = 44100): Int
    fun start(): Int
    fun getChannelCount(): Int
    fun getFramesPerBurst(): Int
    /**
     * Write some audio data to the output stream.
     * @param buffer The audio data to write.
     * @param offset The frame offset in the buffer to start writing from.
     * @param numFrames The number of frames to write.
     * @return The number of frames actually written.
     */
    fun write(buffer: FloatArray,
              offset: Int,
              numFrames: Int): Int
    fun stop()
    fun close()
}

