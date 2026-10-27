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
        // The local publish repository must come before mavenCentral: sample's preBuild publishes
        // the current sources into repository/, and a later position lets the stale 0.0.1 artifact
        // on Central shadow it.
        maven(url = uri("repository"))
        mavenCentral()
    }
}

rootProject.name = "twt-visitor-android"
include(":visitor-sdk")
include(":sample")
