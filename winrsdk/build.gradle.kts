plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

android {
    // Unit tests run against unminified debug classes only — the release
    // variant's R8 pass obfuscates internals and made testReleaseUnitTest
    // fail on ClassNotFound for every suite (audit blocker).

    namespace = "com.avafli.winrsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // R8 shrinking/obfuscation enabled. The SDK's public API, serializers, and
            // reflection targets are preserved via consumer-rules.pro (applied to consumers)
            // and proguard-rules.pro (applied to this module).
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // android.util.Log (Logger.error/warn) must not crash JVM unit tests —
        // the error paths under test call it. Log.* returns defaults instead.
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Secure Storage
    implementation(libs.security.crypto)

    // Optional: Ad ID
    compileOnly(libs.play.services.ads.identifier)

    // Optional: FCM
    compileOnly(libs.firebase.messaging)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.avafli"
                artifactId = "winrsdk"
                version = "2.6.0"

                pom {
                    name.set("WINR SDK")
                    description.set("Sweepstakes & prizing SDK for Android app publishers")
                    url.set("https://github.com/avafli/winr-android-sdk")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("avafli")
                            name.set("Avafli")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/avafli/winr-android-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/avafli/winr-android-sdk.git")
                        url.set("https://github.com/avafli/winr-android-sdk/tree/main")
                    }
                }
            }
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.enableUnitTest = false
    }
}
