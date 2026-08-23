plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version libs.versions.agp.get() apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}

// Stable APK releases ship only formal plugins. Demo plugins remain available for development
// and CI verification, but are intentionally excluded from user-facing release assets.
val stablePluginPackageTasks = listOf(
    ":plugins:ai-translation:packagePlugin",
    ":plugins:amll-ttml:packagePlugin",
)

tasks.register("packageReleasePlugins") {
    group = "plugin distribution"
    description = "Build the formal HyperLyric plugins included in stable APK releases."
    dependsOn(stablePluginPackageTasks)
}
