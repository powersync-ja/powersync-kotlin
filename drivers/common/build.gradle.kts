import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    powersyncTargets()
    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.sqlite)
        }

        val commonJava by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.sqlite.jdbc)
            }
        }

        jvmMain {
            dependsOn(commonJava)
        }

        androidMain {
            dependsOn(commonJava)
        }

        nativeMain.dependencies {
            implementation(libs.androidx.sqliteFramework)
        }

        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
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
