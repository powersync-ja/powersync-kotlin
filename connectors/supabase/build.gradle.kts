import com.powersync.plugins.sonatype.setupGithubRepository
import com.powersync.plugins.utils.powersyncTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    powersyncTargets()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.supabase.client)
            api(libs.supabase.auth)
        }
    }
}

android {
    namespace = "com.powersync.connector.supabase"
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

setupGithubRepository()
