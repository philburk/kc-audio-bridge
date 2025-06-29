package org.mobileer.audiobridge;

import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class JavaSoundOutputStream {

    private static final int BUFFER_SIZE_FRAMES = 2048; // Buffer size in frames

    private int mSampleRate;
    private SourceDataLine mSourceDataLine;
    private byte[] mByteBuffer;
    private static final int STEREO = 2; // 2 channels: left and right

    public int open(int sampleRate) {
        mSampleRate = sampleRate;
        // Stereo, 16-bit, signed PCM, little-endian
        AudioFormat format = new AudioFormat((float) mSampleRate, 16, STEREO, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Line not supported");
            return -1;
        }

        try {
            mSourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            // buffer size in bytes: frames * bytes_per_sample * num_channels
            mSourceDataLine.open(format, BUFFER_SIZE_FRAMES * 2 * STEREO);
        } catch (LineUnavailableException e) {
            System.err.println("Line unavailable: " + e.getMessage());
            return -2;
        }

        // For 16-bit stereo: each frame has 2 samples, each sample is 2 bytes
        mByteBuffer = new byte[BUFFER_SIZE_FRAMES * 2 * 2];
        mSourceDataLine.start();
        return 0;
    }

    public int getSampleRate() {
        if (mSourceDataLine == null) {
            return 0;
        }
        return mSampleRate;
    }

    /**
     * Write some stereo audio data to the output stream.
     * @param buffer
     * @param offset
     * @param numFrames
     * @return number of frames written
     */
    public int write(@NotNull float[] buffer, int startFrame, int numFrames) {
        if (mSourceDataLine == null || !mSourceDataLine.isOpen()) {
            return 0;
        }

        // numFrames is the number of stereo frames. Each frame has 2 samples (left and right).
        // The input buffer contains interleaved samples: L0, R0, L1, R1, ...
        // So, the number of float samples to process is numFrames * 2.
        int numSamplesToProcess = numFrames * STEREO;

        if (numSamplesToProcess * STEREO > mByteBuffer.length) { // Each sample becomes 2 bytes
            // This should not happen if buffer size is managed correctly
            System.err.println("Error: numFrames requires a larger internal buffer than available.");
            // Potentially adjust numFrames or handle error, here we'll truncate for simplicity
            numSamplesToProcess = mByteBuffer.length / 2;
            numFrames = numSamplesToProcess / 2;
        }

        // Convert float samples to 16-bit PCM byte array
        int startSample = startFrame * STEREO;
        for (int i = 0; i < numSamplesToProcess; i++) {
            // Clamp the float value to the range [-1.0, 1.0]
            float sample = Math.max(-1.0f, Math.min(1.0f, buffer[startSample + i]));
            // Scale to 16-bit integer range
            short pcmSample = (short) (sample * Short.MAX_VALUE);
            // Little-endian byte order
            mByteBuffer[i * 2] = (byte) (pcmSample & 0xFF);
            mByteBuffer[i * 2 + 1] = (byte) ((pcmSample >> 8) & 0xFF);
        }

        // Write data to the SourceDataLine
        // bytesToWrite = numFrames * numChannels * bytesPerSample = numFrames * 2 * 2
        int bytesToWrite = numFrames * 2 * STEREO;
        int bytesWritten = mSourceDataLine.write(mByteBuffer, 0, bytesToWrite);
        if (bytesWritten < bytesToWrite && bytesWritten != -1) { // -1 indicates error
            // Handle partial write if necessary, though mSourceDataLine.write should block
            // or write all available data if the buffer is large enough.
            System.err.println("Partial write to SourceDataLine. Expected: " + bytesToWrite + ", Actual: " + bytesWritten);
        } else if (bytesWritten == -1) {
            System.err.println("Error writing to SourceDataLine.");
        }
        return (bytesWritten < 0) ? bytesWritten : (bytesWritten / (2 * STEREO));
    }

    public void close() {
        if (mSourceDataLine != null) {
            mSourceDataLine.drain(); // Wait for all data to be played
            mSourceDataLine.stop();
            mSourceDataLine.close();
            mSourceDataLine = null;
        }
        mByteBuffer = null;
    }
}
