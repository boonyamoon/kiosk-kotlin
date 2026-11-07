import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

val kioskApiBaseUrl: String = providers.gradleProperty("kioskApiBaseUrl").get()

// Release signing: keystore.properties at the project root (never committed, see keystore.properties.example).
// Without it, release builds come out unsigned and can't be installed.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.pos.kiosk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pos.kiosk"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$kioskApiBaseUrl\"")
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            // Drops resources only the unused parts of the libraries reference (no getIdentifier lookups in the app).
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    // The profile is generated on demand (./gradlew :app:generateBaselineProfile, see baselineprofile/README.md)
    // and checked in under src/release/generated; normal builds just package it.
    automaticGenerationDuringBuild = false
    // Also lay out the dex so startup classes sit together (uses the startup profile from the same run).
    dexLayoutOptimization = true
}

// nonMinifiedRelease / benchmarkRelease are added by the baseline profile plugin and are only ever installed
// on a test device to generate or measure the profile, usually against a local http backend: sign them with
// the debug key and allow cleartext. Shipped release builds are untouched.
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { it.useProfilingSetup() }
    onVariants(selector().withBuildType("benchmarkRelease")) { it.useProfilingSetup() }
}

fun com.android.build.api.variant.ApplicationVariant.useProfilingSetup() {
    signingConfig.setConfig(android.signingConfigs.getByName("debug"))
    manifestPlaceholders.put("usesCleartextTraffic", "true")
}

// A shipped build must talk to the real backend: refuse to build release against the local dev default
// (gradle.properties points at http://10.0.2.2:8080/) or any plain-http URL, which release blocks anyway.
// Pass it on the command line: ./gradlew :app:bundleRelease -PkioskApiBaseUrl=https://api.example.com/
val checkReleaseApiUrl by tasks.registering {
    val url = kioskApiBaseUrl
    doLast {
        require(url.startsWith("https://") && "10.0.2.2" !in url && "localhost" !in url && url.endsWith("/")) {
            "Release needs the production API: -PkioskApiBaseUrl=https://<pos-services host>/ (got \"$url\")"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseApiUrl) }

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Built-in printers of all-in-one POS devices (core/printer/builtin); idle on any other hardware. iMin publishes
    // SDK 1.0 only as a jar and SDK 2.0 through JitPack, so both are pinned here as the vendor's released jars.
    implementation(libs.sunmi.printerx)
    implementation(files("libs/iMinPrinter_SDK2_V2.0.0.19_20260422.jar"))
    implementation(files("libs/iminPrinterSDK-14_V1.3.1_2408141540.jar"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Installs the bundled baseline profile on devices where the store doesn't (sideloaded kiosk APKs).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
