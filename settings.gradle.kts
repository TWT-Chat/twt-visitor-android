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
        // Staging directory of the Maven Central publication (tools/push.sh maven android uploads from it);
        // it stays first so a locally published artifact wins over the same version on Central.
        maven(url = uri("repository"))
        mavenCentral()
    }
}

rootProject.name = "twt-visitor-android"
include(":visitor-sdk")
include(":sample")
