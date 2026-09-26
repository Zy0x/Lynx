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
        // JitPack required for TopJohnWu LibSU
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "LynxCompanion"
include(":app")
