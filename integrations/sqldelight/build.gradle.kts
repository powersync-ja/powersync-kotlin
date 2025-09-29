import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.kotlin.atomicfu)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    id("dokka-convention")
}

kotlin {
    powersyncTargets()
    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
            api(libs.sqldelight.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // Separate project because SQLDelight can't generate code in test source sets.
            implementation(projects.integrations.sqldelightTestDatabase)

            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.io)
            implementation(libs.test.turbine)
            implementation(libs.test.coroutines)
            implementation(libs.test.kotest.assertions)

            implementation(libs.sqldelight.coroutines)
        }
    }
}

android {
    namespace = "com.powersync.drivers.common"
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

dokka {
    moduleName.set("PowerSync for SQLDelight")
}
