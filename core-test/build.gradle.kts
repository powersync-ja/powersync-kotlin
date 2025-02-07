import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
//    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinter)
}

kotlin {
//    androidTarget()
    jvm()
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    explicitApi()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        commonMain.dependencies {
            implementation(project(":core"))

        }

//        androidUnitTest.dependencies {
//            implementation("org.robolectric:robolectric:4.14")
//        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.stately.concurrency)
        }
    }
}

/*
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

    namespace = "com.powersync"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
}*/
