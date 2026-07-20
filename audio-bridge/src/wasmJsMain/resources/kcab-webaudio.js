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
let activeOutputDeviceName = "Default Output";
let activeInputDeviceName = "Default Input";

const STEREO = 2;

// Queue parameters
const capacityInFrames = 2048; // Must be power of two.
const capacityInSamples = capacityInFrames * STEREO;
const capacityFrameMask = capacityInFrames - 1; // bit mask
const capacitySampleMask = capacityInSamples - 1;  // bit mask

// Define offsets in the shared int buffer for the FIFO control
const INDEX_FRAMES_WRITTEN = 0;
const INDEX_FRAMES_READ = 1;
const INDEX_CAPACITY = 2;
const INDEX_FRAMES_UNDERFLOWED = 3;
const NUM_FIFO_INTS = 4;

// Active Output Shared arrays
let activeFloatSharedBuffer = null;
let activeSharedFloatArray = null;
let activeIntSharedBuffer = null;
let activeSharedIntArray = null;

let wasmDeviceList = [];

async function updateWasmDeviceList() {
    try {
        const devices = await navigator.mediaDevices.enumerateDevices();
        wasmDeviceList = devices.map(d => ({
            id: d.id || d.deviceId || "",
            label: d.label || (d.kind === "audioinput" ? "Microphone" : "Speaker"),
            kind: d.kind
        }));
    } catch (e) {
        wasmDeviceList = [];
    }
}

if (navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function') {
    updateWasmDeviceList();
    navigator.mediaDevices.addEventListener('devicechange', updateWasmDeviceList);
}

function getWasmDevicesCount(kind) {
    return wasmDeviceList.filter(d => d.kind === kind).length;
}

function getWasmDeviceName(kind, index) {
    const filtered = wasmDeviceList.filter(d => d.kind === kind);
    return filtered[index] ? filtered[index].label : "";
}

function getWasmDeviceId(kind, index) {
    const filtered = wasmDeviceList.filter(d => d.kind === kind);
    return filtered[index] ? filtered[index].id : "";
}

function stringHash(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash |= 0;
    }
    return hash;
}

function findWasmDeviceIdFromHash(kind, hash) {
    const match = wasmDeviceList.find(d => d.kind === kind && stringHash(d.id) === hash);
    return match ? match.id : "";
}

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
    return activeSharedIntArray ? Atomics.load(activeSharedIntArray, INDEX_FRAMES_WRITTEN) : 0;
}

function getOutputFramesRead() {
    return activeSharedIntArray ? Atomics.load(activeSharedIntArray, INDEX_FRAMES_READ) : 0;
}

function setOutputFramesWritten(framesWritten) {
    if (activeSharedIntArray) {
        Atomics.store(activeSharedIntArray, INDEX_FRAMES_WRITTEN, framesWritten);
    }
}

// Function to write a pair of float values to the shared buffer
function setAudioPair(framesWritten, left, right) {
    if (activeSharedFloatArray) {
        const writeIndex = (framesWritten * STEREO) & capacitySampleMask;
        activeSharedFloatArray[writeIndex] = left;
        activeSharedFloatArray[writeIndex + 1] = right;
    }
}

function startWebAudio(deviceIdHash = -1) {
    if (!window.crossOriginIsolated
            && window.location.hostname !== "localhost"
            && window.location.hostname !== "127.0.0.1") {
        console.warn(`SharedArrayBuffer might not work because the page is not cross-origin isolated.`);
    }
    try {
        if (!audioContext) {
            audioContext = new AudioContext();
        }

        console.log("startWebAudio: initializing new SharedArrayBuffers for output playback...");
        const capacityInSamples = capacityInFrames * STEREO;
        const floatBufferSizeBytes = Float32Array.BYTES_PER_ELEMENT * capacityInSamples;
        activeFloatSharedBuffer = new SharedArrayBuffer(floatBufferSizeBytes);
        activeSharedFloatArray = new Float32Array(activeFloatSharedBuffer);

        const intBufferSizeBytes = Int32Array.BYTES_PER_ELEMENT * NUM_FIFO_INTS;
        activeIntSharedBuffer = new SharedArrayBuffer(intBufferSizeBytes);
        activeSharedIntArray = new Int32Array(activeIntSharedBuffer);

        activeSharedIntArray[INDEX_FRAMES_WRITTEN] = 0;
        activeSharedIntArray[INDEX_FRAMES_READ] = 0;
        activeSharedIntArray[INDEX_CAPACITY] = capacityInFrames;
        activeSharedIntArray[INDEX_FRAMES_UNDERFLOWED] = 0;

        // Resolve output device name synchronously
        activeOutputDeviceName = "Default Output";
        if (deviceIdHash !== -1) {
            const matchedId = findWasmDeviceIdFromHash("audiooutput", deviceIdHash);
            if (matchedId) {
                const device = wasmDeviceList.find(d => d.kind === "audiooutput" && d.id === matchedId);
                if (device) {
                    activeOutputDeviceName = device.label;
                }
            }
        } else {
            const defaultDevice = wasmDeviceList.find(d => d.kind === "audiooutput" && (d.id === "default" || d.id === ""));
            if (defaultDevice) {
                activeOutputDeviceName = defaultDevice.label;
            } else {
                const firstDevice = wasmDeviceList.find(d => d.kind === "audiooutput");
                if (firstDevice) {
                    activeOutputDeviceName = firstDevice.label;
                }
            }
        }

        audioContext.audioWorklet.addModule('kcab-output-stream.js').then(() => {
            outputWorkletNode = new AudioWorkletNode(audioContext,
                    'output-stream', {
                    numberOfInputs: 0,
                    numberOfOutputs: 1,
                    outputChannelCount: [STEREO],
                    processorOptions: {
                        floatSharedBuffer: activeFloatSharedBuffer,
                        intSharedBuffer: activeIntSharedBuffer
                    }
            });
            outputWorkletNode.connect(audioContext.destination);

            if (audioContext.state === 'suspended') {
                audioContext.resume();
            }

            // Output device routing
            if (deviceIdHash !== -1 && typeof audioContext.setSinkId === 'function') {
                const matchedId = findWasmDeviceIdFromHash("audiooutput", deviceIdHash);
                console.log("startWebAudio: output routing deviceIdHash =", deviceIdHash, "matchedId =", matchedId);
                if (matchedId) {
                    audioContext.setSinkId(matchedId).then(() => {
                        console.log("startWebAudio: setSinkId succeeded for", matchedId);
                        if (audioContext.state === 'suspended') {
                            return audioContext.resume();
                        }
                    }).then(() => {
                        console.log("startWebAudio: AudioContext state after setSinkId:", audioContext.state);
                    }).catch(err => {
                        console.error("Error setting output sinkId:", err);
                    });
                }
            }
            console.log("startWebAudio: output node connected successfully.");
        }).catch(err => {
            console.error("Error adding module or starting output node:", err);
        });

    } catch (error) {
        console.error('Error setting up AudioWorklet for CustomOutputStream:', error);
    }
}

async function stopWebAudio() {
    activeOutputDeviceName = "None";
    if (audioContext) {
        if (outputWorkletNode) {
            try {
                outputWorkletNode.disconnect();
            } catch (e) {
                console.warn('Error disconnecting outputWorkletNode:', e);
            }
            outputWorkletNode = null;
        }
        await audioContext.suspend().then(() => {
            console.log('AudioContext suspended.');
        });
    }
}

function showJavaScriptAlert() {
    alert("This is from a JavaScript function.");
}

let inputWorkletNode;
let mediaStream;
let mediaStreamSource;

// Input Queue parameters (2048 frames capacity, 1 channel/mono)
const inputCapacityInFrames = 2048;
const INPUT_CHANNELS = 1;
const inputCapacityInSamples = inputCapacityInFrames * INPUT_CHANNELS;
const inputCapacitySampleMask = inputCapacityInSamples - 1;

let activeInputFloatSharedBuffer = null;
let activeInputSharedFloatArray = null;
let activeInputIntSharedBuffer = null;
let activeInputSharedIntArray = null;

function getInputFramesPerBurst() {
    return 128;
}

function getInputCapacityInFrames() {
    return inputCapacityInFrames;
}

function getInputFramesWritten() {
    return activeInputSharedIntArray ? Atomics.load(activeInputSharedIntArray, INDEX_FRAMES_WRITTEN) : 0;
}

function getInputFramesRead() {
    return activeInputSharedIntArray ? Atomics.load(activeInputSharedIntArray, INDEX_FRAMES_READ) : 0;
}

function setInputFramesRead(framesRead) {
    if (activeInputSharedIntArray) {
        Atomics.store(activeInputSharedIntArray, INDEX_FRAMES_READ, framesRead);
    }
}

function getAudioInputSample(framesRead, channel) {
    if (activeInputSharedFloatArray) {
        const readIndex = (framesRead * INPUT_CHANNELS + channel) & inputCapacitySampleMask;
        return activeInputSharedFloatArray[readIndex];
    }
    return 0.0;
}

function startWebAudioInput(deviceIdHash = -1) {
    try {
        if (!audioContext) {
            audioContext = new AudioContext();
        }

        console.log("startWebAudioInput: initializing new SharedArrayBuffers for input recording...");
        const inputCapacityInSamples = inputCapacityInFrames * INPUT_CHANNELS;
        const floatBufferSizeBytes = Float32Array.BYTES_PER_ELEMENT * inputCapacityInSamples;
        activeInputFloatSharedBuffer = new SharedArrayBuffer(floatBufferSizeBytes);
        activeInputSharedFloatArray = new Float32Array(activeInputFloatSharedBuffer);

        const intBufferSizeBytes = Int32Array.BYTES_PER_ELEMENT * NUM_FIFO_INTS;
        activeInputIntSharedBuffer = new SharedArrayBuffer(intBufferSizeBytes);
        activeInputSharedIntArray = new Int32Array(activeInputIntSharedBuffer);

        activeInputSharedIntArray[INDEX_FRAMES_WRITTEN] = 0;
        activeInputSharedIntArray[INDEX_FRAMES_READ] = 0;
        activeInputSharedIntArray[INDEX_CAPACITY] = inputCapacityInFrames;
        activeInputSharedIntArray[INDEX_FRAMES_UNDERFLOWED] = 0;

        // Resolve input device name synchronously
        activeInputDeviceName = "Default Input";
        if (deviceIdHash !== -1) {
            const matchedId = findWasmDeviceIdFromHash("audioinput", deviceIdHash);
            if (matchedId) {
                const device = wasmDeviceList.find(d => d.kind === "audioinput" && d.id === matchedId);
                if (device) {
                    activeInputDeviceName = device.label;
                }
            }
        } else {
            const defaultDevice = wasmDeviceList.find(d => d.kind === "audioinput" && (d.id === "default" || d.id === ""));
            if (defaultDevice) {
                activeInputDeviceName = defaultDevice.label;
            } else {
                const firstDevice = wasmDeviceList.find(d => d.kind === "audioinput");
                if (firstDevice) {
                    activeInputDeviceName = firstDevice.label;
                }
            }
        }

        const constraints = {
            audio: {
                echoCancellation: false,
                noiseSuppression: false,
                autoGainControl: false
            }
        };

        if (deviceIdHash !== -1) {
            const matchedId = findWasmDeviceIdFromHash("audioinput", deviceIdHash);
            if (matchedId) {
                constraints.audio.deviceId = { exact: matchedId };
            }
        }

        navigator.mediaDevices.getUserMedia(constraints).then(stream => {
            mediaStream = stream;
            const track = mediaStream.getAudioTracks()[0];
            if (track) {
                activeInputDeviceName = track.label;
            }

            audioContext.audioWorklet.addModule('kcab-input-stream.js').then(() => {
                mediaStreamSource = audioContext.createMediaStreamSource(mediaStream);

                inputWorkletNode = new AudioWorkletNode(audioContext, 'input-stream', {
                    numberOfInputs: 1,
                    numberOfOutputs: 0,
                    processorOptions: {
                        floatSharedBuffer: activeInputFloatSharedBuffer,
                        intSharedBuffer: activeInputIntSharedBuffer,
                        channels: INPUT_CHANNELS
                    }
                });

                mediaStreamSource.connect(inputWorkletNode);

                if (audioContext.state === 'suspended') {
                    audioContext.resume();
                }

                console.log("startWebAudioInput: input node connected successfully.");
            }).catch(err => {
                console.error("Error setting up input AudioWorklet:", err);
            });
        }).catch(err => {
            console.error("Error getting user media stream:", err);
        });

        return 0; // OK
    } catch (error) {
        console.error('Error starting Web Audio Input:', error);
        return -1; // ERROR
    }
}

async function stopWebAudioInput() {
    activeInputDeviceName = "None";
    try {
        if (inputWorkletNode) {
            try {
                inputWorkletNode.disconnect();
            } catch (e) {
                console.warn('Error disconnecting inputWorkletNode:', e);
            }
            inputWorkletNode = null;
        }
        if (mediaStreamSource) {
            try {
                mediaStreamSource.disconnect();
            } catch (e) {
                console.warn('Error disconnecting mediaStreamSource:', e);
            }
            mediaStreamSource = null;
        }
        if (mediaStream) {
            mediaStream.getTracks().forEach(track => track.stop());
            mediaStream = null;
        }
    } catch (error) {
        console.error('Error stopping Web Audio Input:', error);
    }
}

async function getWasmAudioPermissionState() {
    try {
        if (!navigator.permissions || !navigator.permissions.query) {
            return "undetermined";
        }
        const result = await navigator.permissions.query({ name: 'microphone' });
        return result.state;
    } catch (e) {
        return "undetermined";
    }
}

async function requestWasmAudioPermission() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach(track => track.stop());
        return "granted";
    } catch (e) {
        return "denied";
    }
}

function getWasmCurrentOutputDeviceName() {
    return activeOutputDeviceName;
}

function getWasmCurrentInputDeviceName() {
    return activeInputDeviceName;
}

window.startWebAudio = startWebAudio;
window.getWasmDevicesCount = getWasmDevicesCount;
window.getWasmDeviceName = getWasmDeviceName;
window.getWasmDeviceId = getWasmDeviceId;
window.startWebAudioInput = startWebAudioInput;
window.stopWebAudioInput = stopWebAudioInput;
window.getInputFramesPerBurst = getInputFramesPerBurst;
window.getInputCapacityInFrames = getInputCapacityInFrames;
window.getInputFramesWritten = getInputFramesWritten;
window.getInputFramesRead = getInputFramesRead;
window.setInputFramesRead = setInputFramesRead;
window.getAudioInputSample = getAudioInputSample;
window.getWasmAudioPermissionState = getWasmAudioPermissionState;
window.requestWasmAudioPermission = requestWasmAudioPermission;
window.stopWebAudio = stopWebAudio;
window.getAudioSampleRate = getAudioSampleRate;
window.showJavaScriptAlert = showJavaScriptAlert;
window.setAudioPair = setAudioPair;
window.getOutputFramesWritten = getOutputFramesWritten;
window.getOutputFramesRead = getOutputFramesRead;
window.getOutputFramesPerBurst = getOutputFramesPerBurst;
window.getOutputCapacityInFrames = getOutputCapacityInFrames;
window.setOutputFramesWritten = setOutputFramesWritten;
window.getWasmCurrentOutputDeviceName = getWasmCurrentOutputDeviceName;
window.getWasmCurrentInputDeviceName = getWasmCurrentInputDeviceName;
