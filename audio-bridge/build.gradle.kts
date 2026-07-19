import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    signing
}

group = "com.softsynth"
version = "0.3.0"

kotlin {
    jvmToolchain(17)
    androidTarget {
        publishLibraryVariants("release", "debug")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AudioBridge"
            isStatic = true
        }
    }

    jvm("desktop") {
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
        }
        binaries.library()
    }

    sourceSets {
        val desktopMain by getting
        val commonMain by getting


        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
        }
    }
}

android {
    namespace = "com.softsynth.audiobridge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        val javadocJarTask = tasks.register("${name}JavadocJar", Jar::class) {
            archiveClassifier.set("javadoc")
            archiveAppendix.set(this@withType.name.lowercase())
        }
        artifact(javadocJarTask)

        pom {
            name.set("kc-audio-bridge")
            description.set("Cross-platform audio output for Kotlin/Compose apps on Android, Web, and Desktop")
            url.set("https://github.com/philburk/kc-audio-bridge")

            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set("philburk")
                    name.set("Phil Burk")
                    email.set("burkphil@gmail.com")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/philburk/kc-audio-bridge.git")
                developerConnection.set("scm:git:ssh://github.com/philburk/kc-audio-bridge.git")
                url.set("https://github.com/philburk/kc-audio-bridge")
            }
        }
    }

    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri(layout.buildDirectory.dir("repos/maven-central"))
        }
    }
}

signing {
    val isRelease = !version.toString().endsWith("SNAPSHOT")
    val hasSigningKey = project.hasProperty("signing.keyId") || 
                         project.hasProperty("signing.key") || 
                         project.hasProperty("signing.secretKeyRingFile")
    isRequired = isRelease && hasSigningKey

    if (hasSigningKey) {
        if (project.hasProperty("signing.key")) {
            val key = project.property("signing.key") as String
            val password = project.property("signing.password") as String
            useInMemoryPgpKeys(key, password)
        }
        sign(publishing.publications)
    }
}
