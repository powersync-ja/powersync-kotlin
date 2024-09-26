import com.powersync.plugins.sonatype.setupGithubRepository

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(project(":persistence"))
            implementation(compose.runtime)
        }
        androidMain.dependencies {
            implementation(compose.foundation)
        }
    }
}

android {
    namespace = "com.powersync.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    kotlin {
        jvmToolchain(17)
    }
}

setupGithubRepository()