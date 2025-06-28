// Read float data from a stereo FIFO and write it to the output buffers.

const STEREO = 2;

// Define offsets in the shared int buffer for the FIFO control.
// These must match the offsets defined in webaudio.js
const INDEX_FRAMES_WRITTEN = 0;
const INDEX_FRAMES_READ = 1;
const INDEX_CAPACITY = 2;

class CustomOutputStream extends AudioWorkletProcessor {

  constructor(options) {
    // The super constructor call is required.
    super(options);
    console.log(`options.numberOfInputs: ${options.numberOfInputs}`);
    console.log(`options.numberOfOutputs: ${options.numberOfOutputs}`);
    console.log(`options.outputChannelCount: ${options.outputChannelCount}`);
    this.sharedFloatArray = new Float32Array(options.processorOptions.floatSharedBuffer);
    this.sharedIntArray = new Int32Array(options.processorOptions.intSharedBuffer);
    this.capacityInFrames = Atomics.load(this.sharedIntArray, INDEX_CAPACITY);
    this.capacityInSamples = this.capacityInFrames * STEREO;
    this.sampleMask = this.capacityInSamples - 1;
    this.callbackCounter = 0;
  }

  process(inputs, outputs, parameters) {
    const framesRead = Atomics.load(this.sharedIntArray, INDEX_FRAMES_READ);
    const output = outputs[0];
    const channelCount = output.length;
    const framesPerBurst = output[0].length;
    if (this.callbackCounter  === 0) {
        console.log(`framesPerBurst: ${framesPerBurst}`);
        console.log(`channelCount: ${channelCount}`);
    }
    // Data in the float array is interleaved.
    // We have to deinterleave it into the output buffers.
    for (let channel = 0; channel < channelCount; ++channel) {
      const outputChannel = output[channel];
        if (this.callbackCounter  === 0) {
            console.log(`channel: ${channel}`);
            console.log(`outputs.length: ${outputs.length}`);
            console.log(`outputChannel.length: ${outputChannel.length}`);
        }
      let sampleOffset = (framesRead * STEREO) + channel;
      for (let i = 0; i < outputChannel.length; ++i) {
        const readIndex = sampleOffset & this.sampleMask;
        //outputChannel[i] = (Math.random() * 2.0) - 1.0;
        outputChannel[i] = this.sharedFloatArray[readIndex];
        sampleOffset += STEREO; // stride
      }
    }
    Atomics.store(this.sharedIntArray, INDEX_FRAMES_READ, framesRead + framesPerBurst);
    this.callbackCounter += 1;
    return true;
  }
}

registerProcessor('output-stream', CustomOutputStream);
