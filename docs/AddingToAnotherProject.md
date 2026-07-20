[Home](/README.md)

# How to Add AudioBridge to Another Project

There are two main ways to use the `audio-bridge` library in another project:
1. **Public Dependency**: Build your code using a dependency from MavenCentral
1.  **Local Dependency Library**: Build the library locally and publish it to your local Maven repo.

If you are testing on WASM, you will also need to copy some JavaScript files using gradle,
and set some browser headers.

## Method 1: Public Dependency on Maven Central

This method involves downloading the code from the central Maven repository.
This is the recommended method for most developers.

### 1. Add mavenCentral
In your consumer project's root `build.gradle.kts` (or module level), make sure `mavenCentral()' is listed as a repository:
```kotlin
repositories {
    mavenCentral()
    google()
}
```

### 2. Add Dependency
Then add the dependency to your source sets (e.g., `commonMain`):
```kotlin
commonMain.dependencies {
    implementation("com.softsynth:audio-bridge:0.3.0")
}
```

Look on [GitHub](https://github.com/philburk/kc-audio-bridge/releases) to find the latest release version.

## Method 2: Private Dependency on Maven Local

This method involves building the library and publishing it to your local Maven repository (`~/.m2/repository`).
This is useful if you want to use the latest unreleased version of kc-audio-bridge, or if
you want to modify kc-audio-bridge.

### 1. Build and Publish
Download the kc-audio-bridge repository from GitHub to your local machine.
Then run the following command in the `kc-audio-bridge` root directory:
```bash
./gradlew :audio-bridge:publishToMavenLocal
```
This will publish the artifacts (Android AAR, Desktop JAR, WasmJS Klib, etc.) to your local Maven cache with the group `com.softsynth` for the latest AudioBridge version.

### 2. Add mavenLocal
In your consumer project's `settings.gradle.kts`, ensure you are not including the module directly.

In your consumer project's root `build.gradle.kts` (or module level), add `mavenLocal()` to your repositories:
```kotlin
repositories {
    mavenCentral()
    mavenLocal() // Add this
    google()
}
```

### 3. Add Dependency
Then add the dependency to your application's source sets (e.g., `commonMain`):
```kotlin
commonMain.dependencies {
    implementation("com.softsynth:audio-bridge:0.3.0")
}
```

# 2. Copy JavaScript resources for local Web testing

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

## Allow Cross-Origin

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
