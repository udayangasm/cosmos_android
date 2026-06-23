import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pos.cosmos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pos.cosmos"
        minSdk = 24
        targetSdk = 36
        versionCode = 84
        versionName = "84"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = Properties().apply {
                load(FileInputStream(rootProject.file("local.properties")))
            }
            storeFile = file(props.getProperty("KEYSTORE_PATH", "cosmos-pos-release.jks"))
            storePassword = props.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = props.getProperty("KEY_ALIAS", "")
            keyPassword = props.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel & Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Bluetooth
    implementation(libs.bluetooth.scanning)
    implementation(libs.esc.pos.printer)

    // PDF Generation
    implementation(libs.itext.core)

    // Charts - Vico
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Image Loading - Coil
    implementation(libs.coil.compose)

    // Location Services
    implementation(libs.play.services.location)

    // Security - Encrypted SharedPreferences
    implementation(libs.androidx.security.crypto)

    // Room - Local Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager - Periodic background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Barcode Scanning & Object Detection
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}