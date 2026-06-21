[Home](/README.md)

# How to Add AudioBridge to Another Project

There are two main ways to use the `audio-bridge` library in another project:
1.  **Distributable Library**: Build the library locally and add it as a dependency.
2.  **Git Submodule**: Include the source code directly in your project.

## Method 1: Distributable Library (Maven Local)

This method involves building the library and publishing it to your local Maven repository (`~/.m2/repository`). This is useful if you want to reuse the compiled library across multiple projects on your machine.

### 1. Build and Publish
Download the kc-audio-bridge repository from GitHub to your local machine.
Then run the following command in the `kc-audio-bridge` root directory:
```bash
./gradlew :audio-bridge:publishToMavenLocal
```
This will publish the artifacts (Android AAR, Desktop JAR, WasmJS Klib, etc.) to your local Maven cache with the group `com.mobileer` for the latest AudioBridge version.

### 2. Add Dependency
In your consumer project's `settings.gradle.kts`, ensure you are not including the module directly.

In your consumer project's root `build.gradle.kts` (or module level), add `mavenLocal()` to your repositories:
```kotlin
repositories {
    mavenCentral()
    mavenLocal() // Add this
    google()
}
```

Then add the dependency to your source sets (e.g., `commonMain`):
```kotlin
commonMain.dependencies {
    implementation("com.mobileer:audio-bridge:0.3.0")
}
```
### 3. Copy JavaScript resources for local Web testing

## WasmJS Specifics

For **WasmJS** targets, the `audio-bridge` library relies on some external JavaScript files (`kcab-webaudio.js` and `kcab-output-stream.js`) to interface with the Web Audio API.
See the [KSyn demo](https://github.com/philburk/ksyn/blob/main/demo/build.gradle.kts) for an example.

The following code needs to be added to your "webApp/build.gradle.kts" file:

```kotlin
// Define a task that copies the required JS files from the kc-audio-bridge JAR
// into the build directory so the development server can find them.
val copyAudioBridgeJsFiles = tasks.register<Copy>("copyAudioBridgeJsFiles") {
    // Lazily get the wasmJs runtime classpath configuration.
    // This avoids resolving it during the configuration phase.
    val wasmJsRuntime = configurations.named("wasmJsRuntimeClasspath")

    // The 'from' action will now execute later, during the execution phase.
    // At this point, all dependencies (JARs and projects) are properly resolved.
    from(wasmJsRuntime.map { configuration ->
        // We find the specific JAR we need from the resolved files.
        configuration.files.filter { it.isFile && it.name.startsWith("audio-bridge") }
            .map { zipTree(it) }
    }) {
        // Only include the JS files we absolutely need from the JAR.
        include("kcab-webaudio.js")
        include("kcab-output-stream.js")
    }

    // Set the destination directory for the copied files.
    into(layout.buildDirectory.dir("processedResources/wasmJs/main"))
}

// This dependency hook remains the same.
tasks.named("wasmJsProcessResources") {
    dependsOn(copyAudioBridgeJsFiles)
}
```

### Allow Cross-Origin

The WebAudio implementation of the AudioBridge uses SharedArrayBuffer. That requires a special permission to allow "Cross-Origin" operation.
To solve this for local testing, add a folder called webpack.config.d to your webApp folder. It should contain a file called "devServerHeaders.js" which contains:
```
// Configures webpack-dev-server to send the headers required for SharedArrayBuffer.
if (config.devServer) {
    config.devServer.headers = {
        ...config.devServer.headers,
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Resource-Policy": "same-site",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
```

An example is [here](https://github.com/philburk/kc-audio-bridge/blob/main/composeApp/webpack.config.d/devServerHeaders.js).

---

## Method 2: Git Submodule (Source Integration)

This method is best if you want to modify `audio-bridge` while working on your app, or if you want to ensure your app always builds with a specific commit of the library.

### 1. Add Submodule
Add the repository as a submodule to your project:
```bash
git submodule add https://github.com/philburk/kc-audio-bridge.git audio-bridge-module
```

### 2. Configure Gradle
In your project's `settings.gradle.kts`, include the build:
```kotlin
includeBuild("audio-bridge-module")
```

### 3. Add Dependency
In your project's `build.gradle.kts`:
```kotlin
commonMain.dependencies {
    // Gradle composite build will automatically substitute this with the included build
    implementation("com.mobileer:audio-bridge:0.3.0")
}
```

See the note about "WasmJS Specifics" in Method #1.

