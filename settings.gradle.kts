pluginManagement {
    // The convention plugins (versions, signing, variants) live in their own included build.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "StreamsPlayer"

include(":phone")
include(":wear")
include(":exchange-core")
// Resource-only Watch Face Format package. Its own module by necessity: Google Play refuses a bundle
// that carries both Wear OS logic and a watch face.
include(":watchface")
