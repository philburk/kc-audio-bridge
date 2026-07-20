[Home](/README.md)

# How to Use AudioBridge for Output

See [the App.kt demo code](https://github.com/philburk/kc-audio-bridge/blob/main/composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for a complete example.

First, follow the instructions in [UsingAudioBridge](UsingAudioBridge.md)

Import the AudioInputBridge and the necessary coroutine classes.
```
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.readSuspending
import com.softsynth.audiobridge.writeSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
```
Recording audio input is very similar to playing audio output except you "read" instead of "write".
Also, you need to ask permission, to protect the user's privacy.

Before you show the user a recording UI, you may want to check to see if audio input
is supported on your device. If not display a "not supported" message to your user.
```kotlin
val audioInputSupported = remember { AudioInputBridge.isSupported() }
```
In your app class, get the Context that you will need to ask for audio input permission.
```kotlin
val context = getPlatformContext()
```

Create an AudioInputBridge and open the stream.
The default will be a stereo stream that uses float samples.
```kotlin
val audioInputBridge = AudioInputBridge.create()
val openResult = audioInputBridge.open() // stereo, float
```

Run your audio task in a coroutine.
Start the stream and then render and write the audio in a loop.
```kotlin
val job = GlobalScope.launch(Dispatchers.Default) {
    // If not already GRANTED, ask the user for permission to record.
    var state = AudioInputBridge.getPermissionState(context)
    if (state != AudioPermissionState.GRANTED) {
        state = AudioInputBridge.requestPermission(context)
        if (state != AudioPermissionState.GRANTED) {
            return@launch
        }
    }
    // Loop while recording audio.
    val startResult = audioInputBridge.start()
    while(isActive) {
        renderAudio(stereoBuffer, numFrames) // your synth code goes here
        // Write your audio data to the output.
        val framesWritten = audioInputBridge.writeSuspending(stereoBuffer,
                                           offsetFrames,
                                           numFrames,
                                           timeoutMillis = 1000L)
        
        if (framesWritten < 0) {
            cancel("AudioBridge write error") // Cancel the coroutine
            break
        } else if (framesWritten < bufferSizeFrames) {
            cancel("AudioBridge write timeout") // Cancel the coroutine
            break
        }
    }
}
```

When you are done, cleanup.
```kotlin
audioInputBridge.stop()
audioInputBridge.close()
```

See [demo App.kt](composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for an example.
