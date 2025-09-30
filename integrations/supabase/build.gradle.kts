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
