
class CustomOutputStream extends AudioWorkletProcessor {

  constructor(options) {
    // The super constructor call is required.
    super(options);
    this.sharedFloatArray = new Float32Array(options.processorOptions.floatSharedBuffer);
    this.sharedIntArray = new Int32Array(options.processorOptions.intSharedBuffer);
    this.capacityInFrames = Atomics.load(this.sharedIntArray, 2);
    this.capacityInSamples = this.capacityInFrames * 2; // STEREO
    this.bufferMask = this.capacityInSamples - 1;
  }

  process(inputs, outputs, parameters) {
    const readCursor = Atomics.load(this.sharedIntArray, 1);
    let counter = 0;
    const output = outputs[0];
    for (let channel = 0; channel < outputs.length; ++channel) {
      const outputChannel = output[channel];
      let sampleOffset = readCursor + channel;
      counter += outputChannel.length;
      for (let i = 0; i < outputChannel.length; ++i) {
        const readIndex = sampleOffset & this.bufferMask;
        outputChannel[i] = this.sharedFloatArray[readIndex];
        sampleOffset += outputChannel.length; // stride
      }
    }
    Atomics.store(this.sharedIntArray, 1, readCursor + counter);
    return true;
  }

}

registerProcessor('output-stream', CustomOutputStream);
