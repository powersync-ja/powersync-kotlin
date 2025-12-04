import com.powersync.plugins.utils.powersyncTargets
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    id("com.powersync.plugins.sonatype")
    id("dokka-convention")
    id("com.powersync.plugins.sharedbuild")
}

kotlin {
    powersyncTargets()
    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings {
                optIn("com.powersync.ExperimentalPowerSyncAPI")
            }
        }

        commonMain.dependencies {
            api(projects.common)
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)

            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.io)
            implementation(libs.test.kotest.assertions)
            implementation(libs.test.coroutines)
            implementation(libs.test.turbine)

            implementation(libs.androidx.sqlite.bundled)
        }

        val commonIntegrationTest by creating {
            dependsOn(commonTest.get())
        }

        // We're putting the native libraries into our JAR, so integration tests for the JVM can run as part of the unit
        // tests.
        jvmTest.get().dependsOn(commonIntegrationTest)

        // We have special setup in this build configuration to make these tests link the PowerSync extension, so they
        // can run integration tests along with the executable for unit testing.
        nativeTest.orNull?.dependsOn(commonIntegrationTest)
    }
}

dependencies {
    // We use a room database for testing, so we apply the symbol processor on the test target.
    val targets = listOf(
        "jvm",
        "macosArm64",
        "macosX64",
        "iosSimulatorArm64",
        "iosX64",
        "tvosSimulatorArm64",
        "tvosX64",
        "watchosSimulatorArm64",
        "watchosX64"
    )

    targets.forEach { target ->
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

dokka {
    moduleName.set("PowerSync Room Integration")
}

tasks.withType<LintTask> {
    exclude { it.file.path.contains("build/generated") }
}

tasks.withType<FormatTask> {
    exclude { it.file.path.contains("build/generated") }
}
