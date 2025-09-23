[Home](/README.md)

# How to Deploy AudioBridge app on the Web

The Kotlin code will be compiled to Web Assembly (WASM) and JavaScript.
These files can be placed on your website.

## Create a Configuration to Build an App for your Website

1. Click the red button to stop any running apps. (Android Studio has some trouble running apps on multiple devices as of 2025-09-22)
1. Go to the Configuration menu and select "Edit Configurations...
1. Click the "+" to add a configuration.
1. Select Gradle from the pop menu.
1. Set the name to:   kc-audio-bridge [wasJsBrowserDistribution]
1. Set the Run field to:   wasmJsBrowserDistribution
1. In Gradle Project menu, select: kc-audio-bridge
1. Click OK button.

## Prepare the Website

1. Create a folder to contain your app. I recommend using a dedicated folder because it can get messy.
2. Create a file called .htaccess containing:
   
## Build the App and Upload it Web

1. Click the triangular run button. It will build for a while.
1. When it finishes building, look for the folder "kc-audio-bridge/composeApp/build/dist/wasmJs/productionExecutable".
2. Upload the contents of that folder to your website.
3. Prepare a [TBD]
