import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    // id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") // Temporarily disabled
}

// Cargar keystore.properties para firma de release
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.akiestoy.beacons"
    compileSdk = 35

    // Configuración de firma para release
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.akiestoy.beacons"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.0.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load from secrets.properties
        val secretsFile = rootProject.file("secrets.properties")
        val secretsProperties = Properties()
        if (secretsFile.exists()) {
            secretsProperties.load(secretsFile.inputStream())
            println("🔍 secrets.properties exists and loaded")
            println("🔍 API_BASE_URL from properties: ${secretsProperties.getProperty("API_BASE_URL")}")
        } else {
            println("❌ secrets.properties NOT FOUND at: ${secretsFile.absolutePath}")
        }

        val apiBaseUrl = secretsProperties.getProperty("API_BASE_URL", "http://192.168.100.199:3000")
        println("🔍 Final API_BASE_URL value: $apiBaseUrl")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "SCAN_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("SCAN_INTERVAL_SECONDS", "2")}\"")
        buildConfigField("String", "EVENT_BATCH_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("EVENT_BATCH_INTERVAL_SECONDS", "15")}\"")
        buildConfigField("String", "BEACON_READING_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("BEACON_READING_INTERVAL_SECONDS", "15")}\"")
        buildConfigField("String", "SIGNAL_CHECK_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("SIGNAL_CHECK_INTERVAL_SECONDS", "2")}\"")
        buildConfigField("String", "HEARTBEAT_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("HEARTBEAT_INTERVAL_SECONDS", "60")}\"")
        buildConfigField("String", "EXIT_DELAY_SECONDS", "\"${secretsProperties.getProperty("EXIT_DELAY_SECONDS", "120")}\"")
        buildConfigField("String", "SIGNAL_LOST_THRESHOLD_SECONDS", "\"${secretsProperties.getProperty("SIGNAL_LOST_THRESHOLD_SECONDS", "5")}\"")
        buildConfigField("String", "FILTER_UPDATE_INTERVAL_SECONDS", "\"${secretsProperties.getProperty("FILTER_UPDATE_INTERVAL_SECONDS", "1")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // AltBeacon - Detección de iBeacons
    implementation("org.altbeacon:android-beacon-library:2.20.6")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // WorkManager - para watchdog periódico del servicio
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Networking - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
