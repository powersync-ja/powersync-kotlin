import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sharedbuild")
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kotlin.atomicfu)
}

kotlin {
    powersyncTargets()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.common)

            api(libs.kotlin.test)
            api(libs.test.coroutines)
            api(libs.test.turbine)
            api(libs.test.kotest.assertions)
            api(libs.kermit.test)
            api(libs.ktor.client.mock)
        }

        val platformMain by creating {
            dependsOn(commonMain.get())

            dependencies {
                // :core links SQLite, which is what we want for tests even in the :common project where the public API
                // does not require linking SQLite.
                api(projects.core)
            }
        }

        jvmMain.get().dependsOn(platformMain)
        nativeMain.orNull?.dependsOn(platformMain)
    }
}

android {
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
        }
        debug {
        }
    }

    namespace = "com.powersync.internal.testutils"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
}
