package com.lidesheng.hyperlyric.ui.utils

import com.lidesheng.hyperlyric.ui.page.LicenseItem

object LicenseProvider {
    fun getLicenses(): List<LicenseItem> {
        return listOf(
            LicenseItem("miuix", "YuKongA", "https://github.com/Yukonga/MIUIX"),
            LicenseItem("libxposed API", "libxposed", "https://github.com/libxposed/api"),
            LicenseItem("lyricon", "tomakino", "https://github.com/tomakino/lyricon"),
            LicenseItem("LyricProvider", "tomakino", "https://github.com/tomakino/LyricProvider"),
            LicenseItem("haze", "chrisbanes", "https://github.com/chrisbanes/haze"),
            LicenseItem("kotlinx.serialization", "Kotlin", "https://github.com/Kotlin/kotlinx.serialization"),
            LicenseItem("Jetpack Compose", "Google", "https://developer.android.com/jetpack/compose"),
            LicenseItem("HyperCeiler", "Sevtinge", "https://github.com/ReChronoRain/HyperCeiler"),
            LicenseItem("HyperOShape", "xzakota", "https://github.com/xzakota/HyperOShape"),
            LicenseItem("KernelSU", "tiann", "https://github.com/tiann/KernelSU"),
            LicenseItem("AndroidAnimations", "daimajia", "https://github.com/daimajia/AndroidAnimations"),
            LicenseItem("HiddenApiBypass", "LSPosed", "https://github.com/LSPosed/AndroidHiddenApiBypass"),
            LicenseItem("OpenAI", "OpenAI", "https://openai.com"),
            LicenseItem("Kotlin Coroutines", "Kotlin", "https://github.com/Kotlin/kotlinx.coroutines")
        )
    }
}
