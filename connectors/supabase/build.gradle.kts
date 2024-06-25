import com.powersync.plugins.sonatype.setupGithubRepository
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.supabase.client)
            implementation(libs.supabase.gotrue)
        }
    }
}

android {
    namespace = "com.powersync.connector.supabase"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    kotlin {
        jvmToolchain(17)
    }
}

setupGithubRepository()