// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProps =
    Properties().apply {
        val file = rootProject.file("version.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

android {
    namespace = "io.github.mycena_project.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.mycena_project.app"
        minSdk = 26
        targetSdk = 34
        versionCode = versionProps.getProperty("versionCode", "1").toInt()
        versionName = versionProps.getProperty("versionName", "0.1.0")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    val injectedStoreFile = findProperty("android.injected.signing.store.file")?.toString()
    if (injectedStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(injectedStoreFile)
                storePassword = findProperty("android.injected.signing.store.password")?.toString()
                keyAlias = findProperty("android.injected.signing.key.alias")?.toString()
                keyPassword = findProperty("android.injected.signing.key.password")?.toString()
            }
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            )
    }

    testOptions {
        unitTests.all {
            it.systemProperty(
                "onboarding.copy.dir",
                rootProject.projectDir.resolve("../ui-copy/onboarding").absolutePath,
            )
        }
    }

    buildTypes {
        getByName("debug") {
            // Development APK. Not for phone-to-phone / freeze hardware.
            buildConfigField("boolean", "TECHNICAL_BETA", "false")
        }
        release {
            isMinifyEnabled = true
            // Technical Beta APK (offline / phone-to-phone). Not the Play Store artifact.
            buildConfigField("boolean", "TECHNICAL_BETA", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (injectedStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("../rust-build")
        }
    }

    packaging {
        jniLibs {
            pickFirsts +=
                setOf(
                    "**/libjnidispatch.so",
                    "**/libmycena_ffi.so",
                )
        }

        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes -= setOf("com/sun/jna/**")
        }
    }

}

val copyMiniappBridge =
    tasks.register<Copy>("copyMiniappBridge") {
        from(rootProject.projectDir.resolve("../crates/mycena-miniapp/src/bridge_api.js"))
        into(layout.projectDirectory.dir("src/main/assets/miniapp"))
    }
val copyOnboardingCopy =
    tasks.register<Copy>("copyOnboardingCopy") {
        from(rootProject.projectDir.resolve("../ui-copy/onboarding"))
        into(layout.projectDirectory.dir("src/main/assets/onboarding"))
        include("*.json")
    }
val copyDataHandlingCopy =
    tasks.register<Copy>("copyDataHandlingCopy") {
        from(rootProject.projectDir.resolve("../ui-copy/data-handling"))
        into(layout.projectDirectory.dir("src/main/assets/data-handling"))
        include("*.json")
    }
tasks.named("preBuild").configure {
    dependsOn(copyMiniappBridge)
    dependsOn(copyOnboardingCopy)
    dependsOn(copyDataHandlingCopy)
}

// Fail closed: do not emit an unsigned or implicitly signed release APK.
// Play Store uses bundleRelease + injected signing (unchanged). Phone-to-phone uses
// assembleRelease via scripts/build-technical-beta-apk.sh.
tasks.register("assertTechnicalBetaSigning") {
    doLast {
        val injected = findProperty("android.injected.signing.store.file")?.toString()
        if (injected.isNullOrBlank()) {
            throw GradleException(
                "assembleRelease (Technical Beta APK) requires an explicit keystore. " +
                    "Do not produce an unsigned release APK. " +
                    "Run: ./scripts/build-technical-beta-apk.sh",
            )
        }
        val store = file(injected)
        if (!store.isFile) {
            throw GradleException("Technical Beta keystore not found: $store")
        }
    }
}
afterEvaluate {
    tasks.findByName("packageRelease")?.dependsOn("assertTechnicalBetaSigning")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(project(":uniffi-bindings"))
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")

    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("net.java.dev.jna:jna-platform:5.13.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
