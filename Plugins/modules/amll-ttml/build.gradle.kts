import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.lidesheng.hyperlyric.plugin.amll.ttml"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lidesheng.hyperlyric.plugin.amll.ttml.build"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                // AGP 已禁用非 optimize 默认文件；R8 优化误编译与混淆短名在
                // parent-first 委派下与宿主 dex 撞名两类问题，均通过
                // proguard-rules.pro 中的 -dontoptimize/-dontobfuscate 规避，
                // 仅保留 shrinking 裁剪未使用代码。
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // 宿主通过父 ClassLoader 提供 API，禁止打入插件 ZIP。
    compileOnly(project(":plugins:api"))

    // 宿主 R8 混淆后不保留 Kotlin stdlib 原始类名（proguard 仅保留 kotlin.Metadata），
    // 插件必须自带 Kotlin 运行库，否则运行时报 NoClassDefFoundError: Intrinsics。
    // parent-first 委派下与未混淆宿主的 stdlib 不冲突（宿主优先，插件自带仅作兜底）。
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")

    // 网络（HttpURLConnection）、JSON（org.json）、XML（XmlPullParser）均为 Android 平台 API，
    // 本插件除 Kotlin stdlib 外零新增运行时依赖。
}

val debugApk = layout.buildDirectory.file("outputs/apk/debug/${project.name}-debug.apk")
val releaseApk = layout.buildDirectory.file(
    "outputs/apk/release/${project.name}-release-unsigned.apk"
)

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn("assembleRelease")
    archiveFileName.set("hyperlyric-amll-ttml-plugin.zip")
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
    archiveFileName.set("hyperlyric-amll-ttml-plugin-debug.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/plugin"))

    from(zipTree(debugApk)) {
        include("classes*.dex")
    }
    from("src/main/plugin") {
        include("manifest.json")
    }
}
