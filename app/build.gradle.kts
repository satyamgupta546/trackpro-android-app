plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.trackmate"
    // CHANGED: Use 35 (Android 15 Stable) instead of 36 (Android 16 Preview) to stop crashes
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trackmate"
        minSdk = 26
        // CHANGED: Match compileSdk
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // JAVA 17 CONFIGURATION (This is what you likely meant by "Update with 17")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // 1. Core Android & Compose (UPDATED to fix crashes on newer Android)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // IMPORTANT: Updated BOM to 2024 version to support Android 15/16
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // 2. Extended Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // 3. FREE MAPS (OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.18") // Updated
    implementation("androidx.compose.ui:ui-viewbinding:1.6.8")

    // 4. Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 5. Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 6. Supabase & Networking
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.5.0")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")

    // 7. SERIALIZATION
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}