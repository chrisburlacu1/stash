import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing credentials. Resolution order: local.properties (developer machine, gitignored)
// then environment variables (CI). Never hardcode a keystore path or password here — see
// CLAUDE.md, Security & Data. `local.properties` is already gitignored project-wide, and
// *.jks/*.keystore/keystore.properties are gitignored explicitly for this purpose.
//
// When no credentials are present at all (e.g. a fresh CI checkout with no secrets configured),
// releaseSigningConfig below resolves to null and the release build type falls back to the
// debug signing config, so `./gradlew assembleRelease` still succeeds — signed with a throwaway
// key, unsuitable for Play but fine for verifying R8/shrinking behaviour.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

fun releaseProp(key: String, envKey: String): String? =
    localProperties.getProperty(key) ?: System.getenv(envKey)

val releaseStoreFile = releaseProp("STASH_RELEASE_STORE_FILE", "STASH_RELEASE_STORE_FILE")
val releaseStorePassword = releaseProp("STASH_RELEASE_STORE_PASSWORD", "STASH_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseProp("STASH_RELEASE_KEY_ALIAS", "STASH_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseProp("STASH_RELEASE_KEY_PASSWORD", "STASH_RELEASE_KEY_PASSWORD")
val hasReleaseSigningCredentials = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    // com.example.stash cannot be published to Play (the com.example namespace is reserved for
    // samples/templates). Only the Gradle-level namespace/applicationId change here: the Kotlin
    // source package stays com.example.stash. applicationId is independent of the source
    // package in AGP, and applicationId is what actually can't change post-release, so this is
    // the lower-risk move — a full package rename would touch every file's `package` line and
    // every intra-module import for no functional benefit before the first release.
    namespace = "com.chrisburlacu.stash"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chrisburlacu.stash"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Fall back to the debug signing config when no release credentials are configured
            // (e.g. a clean CI checkout with no secrets), so assembleRelease still produces an
            // installable, R8-shrunk APK for verification. It is not a Play-signed artifact.
            signingConfig = if (hasReleaseSigningCredentials) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material3)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    ksp(libs.mlkit.genai.schema.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jsoup)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
