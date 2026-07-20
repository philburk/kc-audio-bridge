[Home](/README.md)

# How to Use AudioBridge for Output

See [the App.kt demo code](https://github.com/philburk/kc-audio-bridge/blob/main/composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for a complete example.

Import the AudioBridge classes.
```
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
```

Create an AudioBridge and open the stream.
The default will be a stereo stream that uses float samples.
```kotlin
val audioOutputBridge = AudioOutputBridge.create()
val openResult = audioOutputBridge.open() // stereo, float
```

After opening the stream you should check to see what sampleRate you are using.
Then you can adjust your synthesizer to handle pitch properly.
```kotlin
val sampleRate = audioOutputBridge.getSampleRate() // usually 44100
```

Run your audio task in a coroutine.
Start the stream and then render and write the audio in a loop.
```kotlin
val job = GlobalScope.launch(Dispatchers.Default) {
    val startResult = audioOutputBridge.start()
    while(isActive) {
        renderAudio(stereoBuffer, numFrames) // your synth code goes here
        // Write your audio data to the output.
        val framesWritten = audioOutputBridge.writeSuspending(stereoBuffer,
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
audioOutputBridge.stop()
audioOutputBridge.close()
```

See [demo App.kt](composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for an example.
