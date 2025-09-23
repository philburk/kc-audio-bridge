This is a Kotlin/Compose Multiplatform project targeting Android, Web, and Desktop.
It provides a simple and portable audio output API that can be used to play stereo floating point buffers.

To try a simple demo on the web, visit: https://transjam.com/kc-audio-bridge/

# Docs for kc-audio-bridge

* [How to Build and Run the Demo](docs/HowToBuildDemo.md)
* [How to add AudioBridge to an existing project](docs/AddingToAnotherProject.md)
* [How to Deploy on the Web](docs/HowToDeployOnWeb.md)

# More about Kotlin/Compose

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

You can discuss Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues with Kotlin/Compose, you can report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
