[Home](/README.md)

# How to Use AudioBridge

See [the App.kt demo code](https://github.com/philburk/kc-audio-bridge/blob/main/composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for a complete example.

Import the AudioBridge classes.
```
import com.mobileer.audiobridge.AudioOutputBridge
import com.mobileer.audiobridge.AudioResult
```

Create an AudioBridge and open the stream.
The default will be a stereo stream that uses float samples.

    val audioBridge = AudioOutputBridge.create()
    val openResult = audioBridge.open() // stereo, float

After opening the stream you should check to see what sampleRate you are using.
Then you can adjust your synthesizer to handle pitch properly.

    val sampleRate = audioBridge.getSampleRate() // usually 44100

Run your audio task in a coroutine.
Start the stream and then render and write the audio in a loop.

    val job = GlobalScope.launch(Dispatchers.Default) {
      val startResult = audioBridge.start()
      while(isActive) {
        renderAudio(stereoBuffer, numFrames) // your synth code goes here
        // Write your audio data to the output.
        val framesWritten = audioBridge.writeSuspending(stereoBuffer,
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

When you are done, cleanup.

    audioBridge.stop()
    audioBridge.close()

See [demo App.kt](composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for an example.
