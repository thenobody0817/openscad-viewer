plugins {
    alias(libs.plugins.android.application)
    // Kotlin compilation comes from AGP 9's built-in Kotlin support.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.openscad.viewer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.openscad.viewer"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-alpha01"

        // Waydroid is x86_64; physical devices are arm64. Ship only what we target.
        ndk {
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
    }

    // The signing key is committed to this repository on purpose. Anyone may
    // rebuild, re-sign and sideload the app, which is what makes the GPL real
    // rather than decorative on Android (see README "Signing").
    //
    // The passwords below are therefore NOT secrets -- they are obfuscation for
    // a key that is public by design. Do not "fix" these by moving them into
    // environment variables or local properties: the F-Droid build server
    // clones this repo with no local state and must be able to produce a
    // byte-identical signed APK for reproducible-build verification.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/openscad-viewer.p12")
            storePassword = "openscad-viewer"
            keyAlias = "openscad-viewer"
            keyPassword = "openscad-viewer"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Left off deliberately. R8 buys size but adds a large surface for
            // reproducibility drift, and an alpha is not the moment for that.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        // Also drives kotlin.compilerOptions.jvmTarget under built-in Kotlin.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    // OpenSCAD language core, substituted from the included `scad-core` build.
    implementation("com.openscad:scad-core:0.1.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)
}
