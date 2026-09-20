import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

group = "io.github.twt-chat"
version = "0.0.1"

android {
    namespace = "com.twt.visitor.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        @Suppress("DEPRECATION")
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    lint {
        disable += setOf("GradleDependency", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.15.0")
}

// Central 要 javadoc 附件；Kotlin Android 库用占位页即可。
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    val stub = layout.buildDirectory.dir("tmp/javadoc")
    from(stub)
    doFirst {
        val index = stub.get().asFile.resolve("index.html")
        index.parentFile.mkdirs()
        index.writeText("<html><body><p>TWT Visitor SDK</p></body></html>\n")
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "visitor-sdk"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            artifact(javadocJar)
            pom {
                name.set("TWT Visitor SDK")
                description.set("Native container SDK for the TWT visitor chat")
                url.set("https://github.com/TWT-Chat/twt-visitor-android")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://github.com/TWT-Chat/twt-visitor-android")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("TWT-Chat")
                        name.set("TWT")
                        email.set("support@twt.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/TWT-Chat/twt-visitor-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/TWT-Chat/twt-visitor-android.git")
                    url.set("https://github.com/TWT-Chat/twt-visitor-android")
                }
            }
        }
    }
    repositories {
        maven {
            name = "Local"
            url = uri(rootProject.layout.projectDirectory.dir("repository"))
        }
        // 签名后的包先落到本地 staging，再由 tools/push.sh maven 上传 Central Portal。
        maven {
            name = "SonatypeStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

fun pgpSecret(): String? {
    val raw = providers.gradleProperty("signingKey").orNull
        ?: System.getenv("SIGNING_KEY")
        ?: return null
    val text = raw.replace("\\n", "\n").replace("\r", "").trim()
    if (text.isBlank()) return null
    val lines = text.lines()
    if (lines.size < 2) return text
    val begin = lines.first()
    val end = lines.last()
    val compact = lines.drop(1).dropLast(1).joinToString("") { line ->
        line.filterNot(Char::isWhitespace)
    }
    val crc = Regex("=[A-Za-z0-9+/]{4}$").find(compact)
    val body = if (crc != null) compact.removeSuffix(crc.value) else compact
    return buildString {
        appendLine(begin)
        appendLine()
        body.chunked(64).forEach { appendLine(it) }
        if (crc != null) appendLine(crc.value)
        append(end)
    }
}

signing {
    val key = pgpSecret()
    val password = providers.gradleProperty("signingPassword").orNull
        ?: System.getenv("SIGNING_PASSWORD")
    val publishingToCentral = gradle.startParameter.taskNames.any {
        it.contains("SonatypeStaging", ignoreCase = true)
    }
    setRequired { publishingToCentral }
    if (key != null) {
        useInMemoryPgpKeys(key, password.orEmpty())
    }
}

afterEvaluate {
    if (pgpSecret() != null) {
        signing.sign(publishing.publications)
    }
}
