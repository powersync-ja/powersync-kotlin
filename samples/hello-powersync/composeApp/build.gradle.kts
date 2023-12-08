import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.ir.backend.js.compile

plugins {
    alias(projectLibs.plugins.kotlinMultiplatform)
    alias(projectLibs.plugins.kotlinCocoapods)
    alias(projectLibs.plugins.androidApplication)
    alias(projectLibs.plugins.jetbrainsCompose)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        version = "1.0.0"
        summary = "A shared library for Hello PowerSync app"
        homepage = "none"
        ios.deploymentTarget = "15.2"

        framework {
            baseName = "ComposeApp"
            linkerOpts("-lsqlite3")
            export("co.powersync:core")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("co.powersync:core")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(projectLibs.compose.ui.tooling.preview)
            implementation(projectLibs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "co.powersync.demos"
    compileSdk = projectLibs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "co.powersync.demos"
        minSdk = projectLibs.versions.android.minSdk.get().toInt()
        targetSdk = projectLibs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = projectLibs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        debugImplementation(projectLibs.compose.ui.tooling)
    }
}

