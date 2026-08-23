import java.util.Properties

pluginManagement {
    repositories {
        maven("https://api.xposed.info/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }

        val properties = Properties()
        val localProperties = File(rootDir, "local.properties")
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }

        maven {
            url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
            credentials {
                username = properties.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = properties.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "HyperLyric"
include(":app")
include(":plugins:api")
include(":plugins:demo-logger")
include(":plugins:ai-translation")
include(":plugins:amll-ttml")

project(":plugins").projectDir = file("Plugins")
project(":plugins:api").projectDir = file("Plugins/api")
project(":plugins:demo-logger").projectDir = file("Plugins/modules/demo-logger")
project(":plugins:ai-translation").projectDir = file("Plugins/modules/ai-translation")
project(":plugins:amll-ttml").projectDir = file("Plugins/modules/amll-ttml")
