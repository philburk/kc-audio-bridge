/*
 * Copyright 2025 Phil Burk, Mobileer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Read float data from input buffers and write it to a FIFO.

const INDEX_FRAMES_WRITTEN = 0;
const INDEX_FRAMES_READ = 1;
const INDEX_CAPACITY = 2;
const INDEX_FRAMES_OVERFLOWED = 3;

class CustomInputStream extends AudioWorkletProcessor {

  constructor(options) {
    // The super constructor call is required.
    super(options);
    this.sharedFloatArray = new Float32Array(options.processorOptions.floatSharedBuffer);
    this.sharedIntArray = new Int32Array(options.processorOptions.intSharedBuffer);
    this.channels = options.processorOptions.channels || 1;
    this.capacityInFrames = Atomics.load(this.sharedIntArray, INDEX_CAPACITY);
    this.capacityInSamples = this.capacityInFrames * this.channels;
    this.sampleMask = this.capacityInSamples - 1;
  }

  process(inputs, outputs, parameters) {
    const framesWritten = Atomics.load(this.sharedIntArray, INDEX_FRAMES_WRITTEN);
    const framesRead = Atomics.load(this.sharedIntArray, INDEX_FRAMES_READ);

    const input = inputs[0];
    if (!input || input.length === 0) {
      return true; // No active input source yet
    }

    const inputChannelCount = input.length;
    const framesPerBurst = input[0].length;

    // Check space in FIFO
    let usedFrames = framesWritten - framesRead;
    if (usedFrames < 0) {
        usedFrames = 0;
    }
    const framesAvailableToWrite = this.capacityInFrames - usedFrames;

    const framesToWrite = Math.min(framesPerBurst, framesAvailableToWrite);
    if (framesToWrite < framesPerBurst) {
       // Overflow!
       Atomics.add(this.sharedIntArray, INDEX_FRAMES_OVERFLOWED, framesPerBurst - framesToWrite);
    }

    // Write interleaved channel data into the shared float buffer
    for (let i = 0; i < framesToWrite; ++i) {
      const frameIndex = framesWritten + i;
      for (let channel = 0; channel < this.channels; ++channel) {
        // If input has fewer channels, duplicate or pad with zero
        const srcChannel = Math.min(channel, inputChannelCount - 1);
        const sampleValue = input[srcChannel][i];

        const writeIndex = (frameIndex * this.channels + channel) & this.sampleMask;
        this.sharedFloatArray[writeIndex] = sampleValue;
      }
    }

    Atomics.store(this.sharedIntArray, INDEX_FRAMES_WRITTEN, framesWritten + framesToWrite);
    return true;
  }
}

registerProcessor('input-stream', CustomInputStream);
