const capacity = 2048; // Example capacity
const scalingFactor = 32767; // Example scaling factor (adjust as needed)

class CustomOutputStream extends AudioWorkletProcessor {

  // Custom AudioParams can be defined with this static getter.
  static get parameterDescriptors() {
    return [{ name: 'amplitude', defaultValue: 1 }];
  }

  constructor(options) {
    // The super constructor call is required.
    super(options);
    this.sharedFloatArray = new Float32Array(options.processorOptions.floatSharedBuffer);
    this.sharedIntArray = new Int32Array(options.processorOptions.intSharedBuffer);
  }
/*
  dequeue() {
    const headIndex = Atomics.load(this.sharedIntArray, this.capacity);
    const tailIndex = Atomics.load(this.sharedIntArray, this.capacity + 1);

    if (headIndex !== tailIndex) { // Check if queue is not empty
      const scaledValue = this.sharedIntArray[headIndex];
      const value = scaledValue / this.scalingFactor; // Convert back to float
      Atomics.store(this.sharedIntArray, this.capacity, (headIndex + 1) % this.capacity); // Atomically update head
      return value;
    } else {
      return null; // Queue is empty
    }
  }
*/
  process(inputs, outputs, parameters) {
    const readCursor = Atomics.load(this.sharedIntArray, 1);
    let counter = 0;
    const capacity = Atomics.load(this.sharedIntArray, 2);
    const bufferMask = capacity - 1;
    const output = outputs[0];
    const amplitude = parameters.amplitude;
    for (let channel = 0; channel < outputs.length; ++channel) {
      const outputChannel = output[channel];
      let sampleOffset = readCursor + channel;
      counter += outputChannel.length;
      for (let i = 0; i < outputChannel.length; ++i) {
        const readIndex = sampleOffset & bufferMask;
        outputChannel[i] = this.sharedFloatArray[readIndex] * amplitude;
        sampleOffset += outputChannel.length;
      }
    }
    Atomics.store(this.sharedIntArray, 1, readCursor + counter);
    return true;
  }

}

registerProcessor('output-stream', CustomOutputStream);
