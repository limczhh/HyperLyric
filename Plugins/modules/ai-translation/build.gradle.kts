import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.lidesheng.hyperlyric.plugin.ai.translation"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lidesheng.hyperlyric.plugin.ai.translation.build"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // The host supplies the API through the parent ClassLoader; never package it in the ZIP.
    compileOnly(project(":plugins:api"))
    // The plugin has an isolated ClassLoader; SystemUI is not a Kotlin runtime provider.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")

    // Networking and JSON use Android platform APIs, so this plugin has no duplicate host runtime.

    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":plugins:api"))
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:" + libs.versions.kotlin.get())
    testImplementation("org.json:json:20240303")
}

val debugApk = layout.buildDirectory.file("outputs/apk/debug/${project.name}-debug.apk")
val releaseApk = layout.buildDirectory.file(
    "outputs/apk/release/${project.name}-release-unsigned.apk"
)

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn("assembleRelease")
    archiveFileName.set("hyperlyric-ai-translation-plugin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/plugin"))

    from(zipTree(releaseApk)) {
        include("classes*.dex")
    }
    from("src/main/plugin") {
        include("manifest.json")
    }
}

val packageDebugPlugin by tasks.registering(Zip::class) {
    dependsOn("assembleDebug")
    archiveFileName.set("hyperlyric-ai-translation-plugin-debug.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/plugin"))

    from(zipTree(debugApk)) {
        include("classes*.dex")
    }
    from("src/main/plugin") {
        include("manifest.json")
    }
}
