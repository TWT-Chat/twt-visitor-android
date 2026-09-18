plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.twt.visitor.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.twt.visitor.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("GradleDependency", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("io.github.twt-chat:visitor-sdk:0.0.1")
}

tasks.named("preBuild") {
    dependsOn(":visitor-sdk:publishReleasePublicationToLocalRepository")
}
