plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crux.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.crux.assistant"
        minSdk = 26          // ForegroundServiceType + modern SpeechRecognizer behave best on 26+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Local, on-device key-value storage for the manual contact mapping (feature 4).
    // No network, no cloud sync — everything stays in this DataStore file on the phone.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Wake-word engine for "Hey CRUX" (feature: wake word). Porcupine is a lightweight,
    // fully on-device, offline wake-word detector — nothing is streamed anywhere.
    // NOTE: Using Porcupine requires a free Picovoice AccessKey from https://console.picovoice.ai
    // and a custom "Hey CRUX" .ppn wake-word file trained on their console (also free).
    // See voice/WakeWordHelper.kt for exactly where those two things plug in.
    implementation("ai.picovoice:porcupine-android:3.0.2")
}
