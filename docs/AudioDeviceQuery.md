[Home](/README.md)

# How to Query and Route Audio Devices

This document explains how to retrieve the list of available audio devices, handle hot-plugging reactively, and select specific hardware routes.

See [App.kt](file:///Users/phil/Work/kc-audio-bridge/composeApp/src/commonMain/kotlin/com/softsynth/audiodemo/App.kt) for a complete example.

## Querying Available Devices

The library exposes two Kotlin `Flow`s on the `AudioDeviceManager` object to query available output and input devices:
```kotlin
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioDeviceInfo
import kotlinx.coroutines.flow.Flow

val outputDevices: Flow<List<AudioDeviceInfo>> = AudioDeviceManager.outputDevices
val inputDevices: Flow<List<AudioDeviceInfo>> = AudioDeviceManager.inputDevices
```

Each `AudioDeviceInfo` provides:
*   `id: Int` - A stable integer ID representing the device.
*   `name: String` - The display name of the audio hardware.
*   `maxChannels: Int` - The maximum number of channels supported.
*   `isDefault: Boolean` - Whether this device is the default system route.

## Dynamic Updates (Hot-Plugging)

The device flows emit updated lists reactively when the hardware configuration changes (e.g., when a USB microphone or headset is plugged in or unplugged). 

### Platform Implementation
*   **Android**: Hooks dynamically into native `AudioManager` callbacks (`AudioDeviceCallback`).
*   **WASM**: Listens dynamically to Web API `devicechange` events.
*   **JVM (Desktop)**: Polls the JavaSound `AudioSystem` mixers in a background coroutine loop every 2 seconds.

### Collecting Device Lists in Compose
In your Compose UI, you can collect these flows as Compose State using `collectAsState`:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.softsynth.audiobridge.AudioDeviceManager

@Composable
fun DeviceSelector() {
    val outputDevices by AudioDeviceManager.outputDevices.collectAsState(initial = emptyList())
    val inputDevices by AudioDeviceManager.inputDevices.collectAsState(initial = emptyList())

    // Dropdown list or menu rendering
    // ...
}
```

## Querying Optimal Latency Parameters

For optimal low-latency rendering and to minimize OS scheduling jitter and hardware resampling, you can query the system's recommended sample rate and buffer size (in frames):

```kotlin
val optimalSampleRate = AudioDeviceManager.getOptimalSampleRate()      // e.g. 48000
val optimalFramesPerBuffer = AudioDeviceManager.getOptimalFramesPerBuffer() // e.g. 512
```

These values can be passed directly when creating input or output bridges.

## Opening a Stream with a Custom Device

When creating an output or input bridge, you can pass the selected device's `id`. Passing `-1` instructs the bridge to route through the system's default device.

### Example for Output Bridge
```kotlin
val selectedOutputId = // selected device id from menu (or -1 for default)

val bridge = AudioOutputBridge.create {
    deviceId = selectedOutputId
}
val openResult = bridge.open()
```

### Example for Input Bridge
```kotlin
val selectedInputId = // selected device id from menu (or -1 for default)

val bridge = AudioInputBridge.create {
    channels = 1 // Mono recording
    deviceId = selectedInputId
}
val openResult = bridge.open()
```

## Getting the Route in Use

To query the name of the actual physical hardware device associated with an active stream (even if opened with the system default ID `-1`):

```kotlin
val activeDeviceName = bridge.getCurrentDeviceName()
```

*   **Android**: Queries the active `routedDevice` dynamically.
*   **WASM**: Retrieves the exact `track.label` from the dynamic Web MediaStream tracks.
*   **JVM**: Resolves default mixers via `AudioSystem.getMixer(null)` on Windows/Linux and parses the active audio line names on macOS.
