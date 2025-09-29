import com.powersync.plugins.utils.powersyncTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("dokka-convention")
}

kotlin {
    // The Supabase KMP project does not support arm64 watchOS builds
    powersyncTargets(watchOS = false)
    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.supabase.client)
            api(libs.supabase.auth)
            api(libs.supabase.storage)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.test.kotest.assertions)
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

dokka {
    moduleName.set("PowerSync Supabase Connector")
}
