plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    alias(libs.plugins.kotlin.atomicfu)
}

android {
    namespace = "com.powersync.demo.backgroundsync"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.powersync.demo.backgroundsync"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
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

    implementation(projects.demos.supabaseTodolist.shared)

    implementation(compose.material)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kmp.lifecycle.compose)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
}
