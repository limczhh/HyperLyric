plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)

}

android {
    namespace = "com.lidesheng.hyperlyric"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lidesheng.hyperlyric"
        minSdk = 31
        targetSdk = 36
        versionCode = 1921
        versionName = "4.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters += listOf("zh", "en")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

dependencies {
    // --- 基本依赖 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.palette.ktx)

    // libxposed API
    compileOnly("io.github.libxposed:api:101.0.0")
    implementation("io.github.libxposed:service:101.0.0")

    // --- 布局兼容 ---
    implementation(libs.androidx.constraintlayout)

    // --- Compose 核心 ---
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // --- MIUI X 组件库 ---
    implementation(libs.miuix.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.haze)

    // --- 调试工具 ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- 网络与序列化 (在线歌词) ---
    implementation(libs.retrofit2.retrofit)
    implementation(libs.squareup.okhttp3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
}