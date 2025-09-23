[Home](/README.md)

# How to Build the kc-audio-bridge Demo

Kotlin Multiplatform development is supported by Android Studio.
This is a free and full featured IDE.
It can be used for Android but also for desktop, iOS or web development.
You do not need an Android device for development. Android Studio include an Android emulator.
Android Studio also includes an advanced coding AI assistant called Gemini.

## Setting up the IDE
1. Learn about Kotlin Multiplatform Development at: https://www.jetbrains.com/kotlin-multiplatform/
1. Install the latest version of Android Studio from https://developer.android.com/studio
1. Checkout this repository from GitHub at https://github.com/philburk/kc-audio-bridge
1. Launch Android Studio
1. From the File menu, Open a new Project by selecting the folder 'kc-audio-bridge'

## Testing the Android Platform

1. Plug in an Android Phone if you have one. If not the emulator will be used.
1. Select the Android "composeApp" configuration from the menu at the top of the window.
1. Click the triangular run button. It will build for a while.
1. If you see a dialog asking for USB debugging, grant permission.
1. You should see an app appear. Click the START button.
1. Turn up the volume on the Android device (and the host if using emulation.)
1. You should hear a sine wave.

## Testing the Desktop Platform

This runs the app using the Java Virtual Machine on Mac, Windows or Linux.

1. Click the red button to stop any running apps. (Android Studio has some trouble running apps on multiple devices as of 2025-09-22)
1. Go to the Configuration menu and select "Edit Configurations...
1. Click the "+" to add a configuration.
1. Select Gradle from the pop menu.
1. Set the name to: kc-audio-bridge [composeApp:run]
1. Set the Run field to: composeApp:Run
1. Click OK button.
1. Click the triangular run button. It will build for a while.
1. You should see an app appear. Click the START button.

## Testing the Web Platform (WASM)

The Kotlin code will be compiled to Web Assembly (WASM) and can run in a web page.

1. Click the red button to stop any running apps. (Android Studio has some trouble running apps on multiple devices as of 2025-09-22)
1. Go to the Configuration menu and select "Edit Configurations...
1. Click the "+" to add a configuration.
1. Select Gradle from the pop menu.
1. Set the name to: kc-audio-bridge:composeApp [wasmJsBrowserDevelopmentRun]
1. Set the Run field to: wasmJsBrowserDevelopmentRun -t --quiet
1. In Gradle Project menu, select: kc-audio-bridge/composeApp
1. Click OK button.
1. Click the triangular run button. It will build for a while.
1. You should see browser web page appear. Click the START button.
