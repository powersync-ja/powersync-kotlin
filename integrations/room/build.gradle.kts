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
        commonMain.dependencies {
            api(projects.core)

            api(libs.androidx.sqlite)
            api(libs.androidx.room.runtime)
        }
    }
}

dependencies {
    // We use a room database for testing, so we apply the symbol processor on the test target.
    add("kspTest", libs.androidx.room.compiler)
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
