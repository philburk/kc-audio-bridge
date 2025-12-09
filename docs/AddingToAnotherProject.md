[Home](/README.md)

# How to Add AudioBridge to Another Project

There are two main ways to use the `audio-bridge` library in another project:
1.  **Distributable Library**: Build the library locally and add it as a dependency.
2.  **Git Submodule**: Include the source code directly in your project.

## Method 1: Distributable Library (Maven Local)

This method involves building the library and publishing it to your local Maven repository (`~/.m2/repository`). This is useful if you want to reuse the compiled library across multiple projects on your machine.

### 1. Build and Publish
Run the following command in the `kc-audio-bridge` root directory:
```bash
./gradlew :audio-bridge:publishToMavenLocal
```
This will publish the artifacts (Android AAR, Desktop JAR, WasmJS Klib, etc.) to your local Maven cache with the group `com.mobileer` and version `0.1.0`.

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
    implementation("com.mobileer:audio-bridge:0.1.0")
}
```

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
    implementation("com.mobileer:audio-bridge:0.1.0")
}
```

---

## WasmJS Specifics

For **WasmJS** targets, the `audio-bridge` library relies on some external JavaScript files (`kcab-webaudio.js` and `kcab-output-stream.js`) to interface with the Web Audio API.

These files must be available to your application's `index.html` at runtime.

### If using Method 1 or 2:
You likely need to ensure these files are copied to your distribution folder.

1.  **Locate the files:** They are in `audio-bridge/src/wasmJsMain/resources/`.
2.  **Copy them:** Configure your build to copy these files to your distribution output (e.g., `build/dist/wasmJs/productionExecutable`), or manually copy them to your project's `src/wasmJsMain/resources`.
3.  **Update index.html:** Ensure your `index.html` loads the main interface file:
    ```html
    <script type="module" src="kcab-webaudio.js"></script>
    ```

*Note: The `composeApp` demo in this repository demonstrates how to automate this copying in its `build.gradle.kts`.*
