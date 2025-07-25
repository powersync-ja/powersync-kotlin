import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ksp)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    powersyncTargets(watchOS=false)
    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings {
                optIn("com.powersync.ExperimentalPowerSyncAPI")
            }
        }

        commonMain.dependencies {
            api(projects.core)

            api(libs.androidx.sqlite)
            api(libs.androidx.room.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.io)
            implementation(libs.test.kotest.assertions)
            implementation(libs.test.coroutines)
            implementation(libs.test.turbine)
        }
    }
}

dependencies {
    // We use a room database for testing, so we apply the symbol processor on the test target.
    listOf("jvm", "macosArm64", "macosX64", "iosSimulatorArm64", "iosX64").forEach { target ->
        val capitalized = target.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        add("ksp${capitalized}Test", libs.androidx.room.compiler)
    }
}

android {
    namespace = "com.powersync.integrations.room"
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
    kotlin {
        jvmToolchain(17)
    }
}
