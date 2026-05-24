import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val githubProperties = Properties().apply {
    val file = rootProject.file("github.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun githubProperty(name: String): String? =
    githubProperties.getProperty(name)
        ?: System.getenv(name.replace(".", "_").uppercase())
        ?: when (name) {
            "gpr.user" -> System.getenv("GITHUB_ACTOR")
            "gpr.key" -> System.getenv("GITHUB_TOKEN")
            else -> null
        }

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "io.edutor.streammark"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)

    implementation(libs.jlatexmath.android)
    implementation(libs.jlatexmath.android.font.greek)
    implementation(libs.jlatexmath.android.font.cyrillic)
    implementation(libs.commonmark.core)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)

    implementation(libs.coil.compose)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "io.edutor"
            artifactId = "streammark"
            version = "1.0.0"

            pom {
                name.set("StreamMark")
                description.set("Streaming markdown for AI chat on Android Compose")
                url.set("https://github.com/jigar225/streammark")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Edutor")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/jigar225/streammark.git")
                    developerConnection.set("scm:git:ssh://github.com:jigar225/streammark.git")
                    url.set("https://github.com/jigar225/streammark")
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jigar225/streammark")
            credentials {
                username = githubProperty("gpr.user").orEmpty()
                password = githubProperty("gpr.key").orEmpty()
            }
        }
    }
}
