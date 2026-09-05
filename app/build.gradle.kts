plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)

}

android {
    namespace = "com.lidesheng.hyperlyric"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.lidesheng.hyperlyric"
        minSdk = 33
        targetSdk = 37
        versionCode = 1939
        versionName = "7.5"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    afterEvaluate {
        base {
            val vName = android.defaultConfig.versionName ?: "0"
            val vCode = android.defaultConfig.versionCode ?: 0
            archivesName.set("HyperLyric-v${vName}.${vCode}")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

dependencies {
    implementation(project(":plugins:api"))

    // --- 基本依赖 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.hiddenapibypass)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // libxposed API
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    // SuperLyric API
    implementation(libs.superlyricapi)

    // Lyricon Subscriber SDK
    implementation(libs.lyricon.subscriber)

    // --- 布局兼容 ---
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.compose)

    // --- Compose 核心 ---
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.reorderable)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // --- MIUI X 组件库 ---
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.nav.android)
    implementation(libs.androidx.compose.animation.graphics)

    // --- 调试工具 ---
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- 网络与序列化 (在线歌词) ---
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit2.retrofit)
    implementation(libs.squareup.okhttp3)
    implementation(libs.retrofit2.kotlinx.serialization.converter)

    // --- 动画库 (YoYo) ---
    implementation(libs.daimajia.animations) { artifact { type = "aar" } }
    implementation(libs.daimajia.easing) { artifact { type = "aar" } }

}

apply(from = "fetch_contributors.gradle")
tasks.named("preBuild") {
    dependsOn("generateContributors")
}

