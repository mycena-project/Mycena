// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import org.gradle.api.GradleException

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.mycena_project.bindings"
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

// mycena.kt is gitignored. Fail closed with the real generate command — do not invent a second binder.
val uniffiKotlin =
    layout.projectDirectory.file("src/main/kotlin/uniffi/mycena/mycena.kt").asFile
tasks.register("requireUniffiKotlin") {
    doLast {
        if (!uniffiKotlin.isFile || uniffiKotlin.length() == 0L) {
            throw GradleException(
                """
                Generated UniFFI Kotlin is missing (intentionally not tracked in git).
                From the Mycena repository root:
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
