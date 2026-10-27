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
        // 本地发布仓库要排在 mavenCentral 之前：sample 的 preBuild 会把当前源码发到
        // repository/，排后面时会被 Central 上同版本号 0.0.1 的旧产物盖掉。
        maven(url = uri("repository"))
        mavenCentral()
    }
}

rootProject.name = "twt-visitor-android"
include(":visitor-sdk")
include(":sample")
