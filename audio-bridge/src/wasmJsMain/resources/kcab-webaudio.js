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

 // WebAudio interface for AudioBridge.kt

let audioContext;
let outputWorkletNode;

const STEREO = 2;

// Queue parameters
const capacityInFrames = 2048; // Must be power of two.
const capacityInSamples = capacityInFrames * STEREO;
const capacityFrameMask = capacityInFrames - 1; // bit mask
const capacitySampleMask = capacityInSamples - 1;  // bit mask

// Use one SharedArrayBuffer for the float data
// and a second SharedArrayBuffer for the writeCursor, readCursor and capacity
const floatBufferSizeBytes = Float32Array.BYTES_PER_ELEMENT * capacityInSamples;
const floatSharedBuffer = new SharedArrayBuffer(floatBufferSizeBytes);
const sharedFloatArray = new Float32Array(floatSharedBuffer);

// Define offsets in the shared int buffer for the FIFO control
const INDEX_FRAMES_WRITTEN = 0;
const INDEX_FRAMES_READ = 1;
const INDEX_CAPACITY = 2;
const INDEX_FRAMES_UNDERFLOWED = 3;
const NUM_FIFO_INTS = 4;

const intBufferSizeBytes = Int32Array.BYTES_PER_ELEMENT * NUM_FIFO_INTS;
const intSharedBuffer = new SharedArrayBuffer(intBufferSizeBytes);
const sharedIntArray = new Int32Array(intSharedBuffer);

// BTW, 2147403647 is two seconds from numeric overflow, for testing.
// Initialize the queue structure
sharedIntArray[INDEX_FRAMES_WRITTEN] = 0; // framesWritten
sharedIntArray[INDEX_FRAMES_READ] = 0; // framesRead
sharedIntArray[INDEX_CAPACITY] = capacityInFrames;
sharedIntArray[INDEX_FRAMES_UNDERFLOWED] = 0;

function getAudioSampleRate() {
    let sampleRate = 0;
    if (!audioContext) {
        sampleRate = -1;
    } else {
        sampleRate = audioContext.sampleRate;
    }
    // console.log(`Audio sample rate: ${sampleRate}`);
    return sampleRate;
}

function getOutputFramesPerBurst() {
    return 128; // fixed quantum size in WebAudio
}

function getOutputCapacityInFrames() {
    return capacityInFrames;
}

function getOutputFramesWritten() {
    return Atomics.load(sharedIntArray, INDEX_FRAMES_WRITTEN);
}

function getOutputFramesRead() {
    return Atomics.load(sharedIntArray, INDEX_FRAMES_READ);
}

function setOutputFramesWritten(framesWritten) {
    Atomics.store(sharedIntArray, INDEX_FRAMES_WRITTEN, framesWritten);
}

// Function to write a pair of float values to the shared buffer
function setAudioPair(framesWritten, left, right) {
    const writeIndex = (framesWritten * STEREO) & capacitySampleMask;
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
            await audioContext.audioWorklet.addModule('kcab-output-stream.js');
            outputWorkletNode = new AudioWorkletNode(audioContext,
                    'output-stream', {
                    numberOfInputs: 0, // Or 0 if it's a source node
                    numberOfOutputs: 1,
                    outputChannelCount: [STEREO],
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

async function stopWebAudio() {
    if (audioContext) {
        if (outputWorkletNode) {
            outputWorkletNode.disconnect(audioContext.destination);
            // Optionally, you might want to nullify outputWorkletNode here
            // outputWorkletNode = null;
        }
        // Suspending the context can be useful if you plan to resume it later
        // If you're completely done with Web Audio, you might consider closing it,
        // but be aware that a closed context cannot be reopened.
        await audioContext.suspend().then(() => {
            console.log('AudioContext suspended.');
        });
        // If you are completely done and won't use Web Audio again on this page load:
        // await audioContext.close().then(() => {
        //     console.log('AudioContext closed.');
        //     audioContext = null;
        // });
    }
}

function showJavaScriptAlert() {
    alert("This is from a JavaScript function.");
}

window.startWebAudio = startWebAudio;
window.stopWebAudio = stopWebAudio;
window.getAudioSampleRate = getAudioSampleRate;
window.showJavaScriptAlert = showJavaScriptAlert;
window.setAudioPair = setAudioPair;
window.getOutputFramesWritten = getOutputFramesWritten;
window.getOutputFramesRead = getOutputFramesRead;
window.getOutputFramesPerBurst = getOutputFramesPerBurst;
window.getOutputCapacityInFrames = getOutputCapacityInFrames;
window.setOutputFramesWritten = setOutputFramesWritten;
