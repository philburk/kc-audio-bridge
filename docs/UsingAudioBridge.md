[Home](/README.md)

# How to Use AudioBridge

See [the App.kt demo code](https://github.com/philburk/kc-audio-bridge/blob/main/composeApp/src/commonMain/kotlin/com/mobileer/audiodemo/App.kt)
for a complete example.

Import the AudioBridge classes.
```
import com.mobileer.audiobridge.AudioBridge
import com.mobileer.audiobridge.AudioResult
```

Create an AudioBridge and open the stream.
The default will be a stereo stream that uses float samples.

    val audioBridge = AudioBridge()
    val openResult = audioBridge.open() // stereo, float

After opening the stream you should check to see what sampleRate you are using.
Then you can adjust your synthesizer to handle pitch properly.

    val sampleRate = audioBridge.getSampleRate() // usually 44100

Run your audio task in a coroutine.
Start the stream and then render and write the audio in a loop.
If the frameCount is less than numFrames then you should
sleep briefly and then try again to write all the audio.

    val job = GlobalScope.launch(Dispatchers.Default) {
      val startResult = audioBridge.start()
      while(isActive()) {
        renderAudio(stereoBuffer) // your code goes here
        val frameCount = audioBridge.write(stereoBuffer,
                                           offsetFrames, numFrames)
      }
    }

When you are done, cleanup.

    audioBridge.stop()
    audioBridge.close()
