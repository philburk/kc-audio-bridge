
//import exports from "./module.mjs"

let audioContext;
let outputWorkletNode;

// Queue parameters
const capacity = 2048; // TODO Must be power of two.
const capacityMask = capacity - 1;

// Use one SharedArrayBuffer for the float data
// and a second SharedArrayBuffer for the writeCursor, readCursor and capacity
const floatBufferSizeBytes = Float32Array.BYTES_PER_ELEMENT * capacity;
const floatSharedBuffer = new SharedArrayBuffer(floatBufferSizeBytes);
const sharedFloatArray = new Float32Array(floatSharedBuffer);

// Fill the sharedFloatArray with 16 cycles of a sine wave
const numCycles = 16;
const samplesPerCycle = capacity / numCycles;
for (let i = 0; i < capacity; i++) {
  sharedFloatArray[i] = Math.sin(2 * Math.PI * (i / samplesPerCycle));
}

const intBufferSizeBytes = Int32Array.BYTES_PER_ELEMENT * 3; // 3 for writeCursor, readCursor and capacity
const intSharedBuffer = new SharedArrayBuffer(intBufferSizeBytes);
const sharedIntArray = new Int32Array(intSharedBuffer);

// Initialize the queue structure
sharedIntArray[0] = 0; // writeCursor
sharedIntArray[1] = 0; // readCursor
sharedIntArray[2] = capacity; // capacity

// Function to enqueue a float value
function writeAudioSample(value) {
  console.log("writeAudioSample(" + value + ")");
  const writeCursor = Atomics.load(sharedIntArray, 0);
  console.log("writeCursor = " + writeCursor);
  const readCursor = Atomics.load(sharedIntArray, 1);
  const capacityValue = Atomics.load(sharedIntArray, 2);
  console.log("writeAudioSample: readCursor=" + readCursor + ", writeCursor=" + writeCursor + ", capacity=" + capacityValue);

  if (readCursor >= writeCursor) { // Check if queue is not full
    const writeIndex = writeCursor & capacityMask;
    sharedFloatArray[writeIndex] = value;
    Atomics.store(sharedIntArray, 0, writeCursor + 1); // Atomically update writeCursor
    return true; // Enqueue successful
  } else {
    return false; // Queue is full
  }
}

async function startWebAudio() {
    // Crucial for SharedArrayBuffer: Ensure secure context and cross-origin isolation
    if (!window.crossOriginIsolated
            && window.location.hostname !== "localhost"
            && window.location.hostname !== "127.0.0.1") {
        console.warn(`SharedArrayBuffer might not work because the page is not cross-origin isolated.
                      Ensure your server sends COOP and COEP headers.`);
        // You might want to prevent further execution or inform the user
    }
    if (window.isSecureContext === false
            && window.location.hostname !== "localhost"
            && window.location.hostname !== "127.0.0.1") {
        console.warn("SharedArrayBuffer requires a secure context (HTTPS), except for localhost.");
    }

    try {
        if (!audioContext) {
            audioContext = new AudioContext();
            await audioContext.audioWorklet.addModule('output_stream.js');
            outputWorkletNode = new AudioWorkletNode(audioContext, 'output-stream', {
                processorOptions: {
                    floatSharedBuffer: floatSharedBuffer,
                    intSharedBuffer: intSharedBuffer
                }
            });
            outputWorkletNode.connect(audioContext.destination);
        } else {
            audioContext.resume().then(() => {
              if (outputWorkletNode) {
                outputWorkletNode.connect(audioContext.destination)
              }
            });
        }
    } catch (error) {
        console.error('Error setting up AudioWorklet for CustomOutputStream:', error);
    }
}

function testCallingKotlin() {
    alert("This is a JavaScript function. Not Kotlin!");
    //alert(exports.getFunnyText());
}

function showJavaScriptAlert() {
    alert("This is from a JavaScript function.");
}

function setNoiseLevel(level)  {
    // Access the 'amplitude' AudioParam
    const amplitudeParam = noiseWorkletNode.parameters.get('amplitude');
    if (amplitudeParam) {
        amplitudeParam.value = level;
        console.log("Noise level set to " + level);
    }
}

window.startWebAudio = startWebAudio;
window.testCallingKotlin = testCallingKotlin;
window.showJavaScriptAlert = showJavaScriptAlert;
window.setNoiseLevel = setNoiseLevel;
window.writeAudioSample = writeAudioSample;

export { startWebAudio, testCallingKotlin, writeAudioSample };
