plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes the build number (the workflow run number + 100, continuing from the builds made
// before the app had its own repo) so every published APK installs over the previous one.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toIntOrNull() ?: 1

// Where the in-app updater looks for new builds (the rolling pre-release CI publishes).
val updateRepo = System.getenv("GITHUB_REPOSITORY") ?: "Jambo199/VividPulse"
val updateBaseUrl = "https://github.com/$updateRepo/releases/download/vivid-pulse-latest/"

android {
    namespace = "com.jambo199.vividpulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jambo199.vividpulse"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "UPDATE_BASE_URL", "\"$updateBaseUrl\"")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // A fixed key keeps updates installable over older builds (it's the key every Vivid
        // Pulse build so far was signed with). Override it with the SIGNING_* environment variables.
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "vividmusic"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "vividmusic"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "vividmusic"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Failures in full, and what tests print (e.g. what GSMArena's live pages gave).
        unitTests.all {
            it.testLogging {
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    // Background checks: charging, the overnight report, sale alerts and the widgets.
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // The real org.json, so parser tests don't hit Android's stubs.
    testImplementation(libs.org.json)
}
