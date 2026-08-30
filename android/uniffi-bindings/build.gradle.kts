// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import org.gradle.api.GradleException

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "network.mycelium.bindings"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly("net.java.dev.jna:jna:5.13.0")
}

// mycelium.kt is gitignored. Fail closed with the real generate command — do not invent a second binder.
val uniffiKotlin =
    layout.projectDirectory.file("src/main/kotlin/uniffi/mycelium/mycelium.kt").asFile
tasks.register("requireUniffiKotlin") {
    doLast {
        if (!uniffiKotlin.isFile || uniffiKotlin.length() == 0L) {
            throw GradleException(
                """
                Generated UniFFI Kotlin is missing (intentionally not tracked in git).
                From the Mycelium repository root:
                  ./scripts/regenerate-uniffi-kotlin.sh
                JVM unit tests (generate + testDebugUnitTest):
                  ./scripts/android-test.sh
                Development APK (native libs + UniFFI + assembleDebug):
                  ./android/build.sh
                See android/README.md (clean checkout).
                """.trimIndent(),
            )
        }
    }
}
afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
        dependsOn("requireUniffiKotlin")
    }
}
