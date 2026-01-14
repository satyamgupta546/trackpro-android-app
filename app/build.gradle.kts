plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization) // <--- Now this will work!
}

android {
    namespace = "com.example.trackmate"
    // Keep your existing SDK setup
    compileSdk = 34 // If 'release(36)' causes issues, revert to 34 or 35. Otherwise keep your version.

    defaultConfig {
        applicationId = "com.example.trackmate"
        minSdk = 26
        targetSdk = 34 // Matched with standard stable versions
        versionCode = 1
        versionName = "1.0"

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
    // 1. Core Android & Compose
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // 2. Extended Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    // 3. FREE MAPS (OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.16")
    implementation("androidx.compose.ui:ui-viewbinding:1.4.3")

    // 4. Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 5. Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // 6. Supabase & Networking
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.5.0")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")

    // 7. SERIALIZATION (Added to fix your error)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}