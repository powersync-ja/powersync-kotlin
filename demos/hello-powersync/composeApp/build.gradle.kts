import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(projectLibs.plugins.kotlinMultiplatform)
    alias(projectLibs.plugins.cocoapods)
    alias(projectLibs.plugins.androidApplication)
    alias(projectLibs.plugins.jetbrainsCompose)
    alias(projectLibs.plugins.skie)
}

kotlin {
    androidTarget()

//    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        version = "1.0.0"
        summary = "A shared library for Hello PowerSync app"
        homepage = "none"
        ios.deploymentTarget = "15.2"
        podfile = project.file("../iosApp/Podfile")

        pod("powersync-sqlite-core") {
            linkOnly = true
        }

        framework {
            baseName = "composeApp"
            isStatic = true
            export("com.powersync:core")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("com.powersync:core")
            api("com.powersync:connectors")
            implementation(projectLibs.bundles.sqldelight)
            implementation(projectLibs.kotlinx.datetime)
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
    namespace = "com.powersync.demos"
    compileSdk = projectLibs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.powersync.demos"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}