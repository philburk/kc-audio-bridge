
//import exports from "./module.mjs"

let audioContext;
let outputWorkletNode;

// Queue parameters
const capacityInFrames = 1024; // TODO Must be power of two.
const capacityInSamples = capacityInFrames * 2; // STEREO
const capacityFrameMask = capacityInFrames - 1;
const capacitySampleMask = capacityInSamples - 1;

// Use one SharedArrayBuffer for the float data
// and a second SharedArrayBuffer for the writeCursor, readCursor and capacity
const floatBufferSizeBytes = Float32Array.BYTES_PER_ELEMENT * capacityInSamples;
const floatSharedBuffer = new SharedArrayBuffer(floatBufferSizeBytes);
const sharedFloatArray = new Float32Array(floatSharedBuffer);

const intBufferSizeBytes = Int32Array.BYTES_PER_ELEMENT * 3; // 3 for writeCursor, readCursor and capacity
const intSharedBuffer = new SharedArrayBuffer(intBufferSizeBytes);
const sharedIntArray = new Int32Array(intSharedBuffer);

// Initialize the queue structure
sharedIntArray[0] = 0; // framesWritten
sharedIntArray[1] = 0; // framesRead
sharedIntArray[2] = capacityInFrames;

let framesPerBurst = 0;

function getOutputFramesWritten() {
    return sharedIntArray[0];
}

function getOutputFramesRead() {
    return sharedIntArray[1];
}

function getOutputFramesPerBurst() {
    return 128; // fixed quantum size in WebAudio
}

function setOutputFramesWritten(value) {
    sharedIntArray[0] = value;
}

// Function to write a pair of float values to the shared buffer
function setAudioPair(framesWritten, left, right) {
    const writeIndex = (framesWritten * 2) & capacitySampleMask;
    sharedFloatArray[writeIndex] = left;
    sharedFloatArray[writeIndex + 1] = right;
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
                    numberOfInputs: 1, // Or 0 if it's a source node
                    numberOfOutputs: 1,
                    outputChannelCount: [2], // Explicitly request STEREO
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

function showJavaScriptAlert() {
    alert("This is from a JavaScript function.");
}

window.startWebAudio = startWebAudio;
window.showJavaScriptAlert = showJavaScriptAlert;
window.setAudioPair = setAudioPair;
window.getOutputFramesWritten = getOutputFramesWritten;
window.getOutputFramesRead = getOutputFramesRead;
window.getOutputFramesPerBurst = getOutputFramesPerBurst;
window.setOutputFramesWritten = setOutputFramesWritten;

//export { startWebAudio, writeAudioSample };
