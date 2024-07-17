plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.androidLibrary)
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
            api(libs.bundles.sqldelight)
        }

        androidMain.dependencies {
            api(libs.sqldelight.driver.android)
            api(libs.powersync.sqlite.core)
            api(libs.requery.sqlite.android)
        }

        iosMain.dependencies {
            api(libs.sqldelight.driver.ios)
        }
    }
}

android {
    kotlin {
        jvmToolchain(17)
    }


    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    namespace = "com.powersync.persistence"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
}

sqldelight {
    databases {
        create("PsDatabase") {
            packageName.set("com.powersync.persistence")
            generateAsync.set(true)
            dialect(project(":dialect"))
        }
    }
}

