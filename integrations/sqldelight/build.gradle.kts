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
            api(projects.common)
            api(libs.sqldelight.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }

        val commonIntegrationTest by creating {
            dependsOn(commonTest.get())

            dependencies {
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

        // The PowerSync SDK links the core extension, so we can just run tests as-is.
        jvmTest.get().dependsOn(commonIntegrationTest)

        // We have special setup in this build configuration to make these tests link the PowerSync extension, so they
        // can run integration tests along with the executable for unit testing.
        nativeTest.orNull?.dependsOn(commonIntegrationTest)
    }
}

android {
    namespace = "com.powersync.integrations.sqldelight"
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
