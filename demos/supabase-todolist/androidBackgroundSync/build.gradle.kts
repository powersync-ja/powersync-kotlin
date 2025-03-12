plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
}

android {
    namespace = "com.powersync.demo.backgroundsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.powersync.demo.backgroundsync"
        minSdk = 28
        targetSdk = 35
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
    // When copying this example, replace "latest.release" with the current version available
    // at: https://central.sonatype.com/artifact/com.powersync/connector-supabase
    implementation("com.powersync:connector-supabase:latest.release")

    implementation(projects.shared)

    implementation(compose.material)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.compose.lifecycle)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
}
