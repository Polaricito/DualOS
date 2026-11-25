pluginManagement {
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
    }
}

rootProject.name = "DualOS"

include(":app")
// DualOS companion app inside the guest: "Back to One UI" (guest/build.gradle.kts).
include(":guest")
include(":core:model")
include(":core:device")
include(":core:root")
include(":core:safety")
