plugins {
    kotlin("multiplatform")
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(projects.demos.supabaseTodolist.shared)
                implementation(compose.material)
            }
        }
    }
}

android {
    namespace = "com.powersync.demos"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.powersync.demos.TodoAppLite"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}