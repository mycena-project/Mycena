// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import java.util.Properties

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
    namespace = "network.mycelium.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "network.mycelium.app"
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

    buildTypes {
        release {
            isMinifyEnabled = true
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
                    "**/libmycelium_ffi.so",
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
        from(rootProject.projectDir.resolve("../crates/mycelium-miniapp/src/bridge_api.js"))
        into(layout.projectDirectory.dir("src/main/assets/miniapp"))
    }
tasks.named("preBuild").configure { dependsOn(copyMiniappBridge) }

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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")

    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("net.java.dev.jna:jna-platform:5.13.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
