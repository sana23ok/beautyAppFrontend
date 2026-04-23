import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.beautyappfrontend"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.beautyappfrontend"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Django API base URL (trailing slash required for Retrofit).
        // Priority: local.properties → gradle.properties → emulator default.
        // - Emulator: http://10.0.2.2:8000/ (maps to host machine’s localhost)
        // - Physical device: http://YOUR_LAN_IP:8000/ — find IP: ipconfig (IPv4), run: python manage.py runserver 0.0.0.0:8000
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localProperties.load(localFile.inputStream())
        }
        val fromLocal = localProperties.getProperty("api.base.url")?.trim()?.takeIf { it.isNotEmpty() }
        val fromGradle = (findProperty("api.base.url") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        val raw = fromLocal ?: fromGradle ?: "http://10.0.2.2:8000/"
        val apiBaseUrl = if (raw.endsWith("/")) raw else "$raw/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

// Source - https://stackoverflow.com/a
// Posted by iknow, modified by community. See post 'Timeline' for change history
// Retrieved 2026-01-22, License - CC BY-SA 4.0


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("io.coil-kt:coil:2.4.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}