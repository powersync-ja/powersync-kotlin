import com.powersync.plugins.utils.powersyncTargets
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    id("com.powersync.plugins.sonatype")
    id("dokka-convention")
    id("com.powersync.plugins.sharedbuild")
}

kotlin {
    powersyncTargets(
        android = {
            namespace = "com.powersync.integrations.room"
        },
        legacyJavaSupport = false,
        web = true
    )

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

            implementation(libs.androidx.sqlite.async)
            implementation(libs.kotlinx.serialization.json)
        }

        val commonNonWeb = create("commonNonWeb") {
            dependsOn(commonMain.get())

            dependencies {
                api(libs.androidx.sqlite.bundled)
            }
        }

        jvmMain {
            dependsOn(commonNonWeb)
        }
        nativeMain {
            dependsOn(commonNonWeb)
        }
        androidMain {
            dependsOn(commonNonWeb)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.io)
            implementation(libs.test.kotest.assertions)
            implementation(libs.test.coroutines)
            implementation(libs.test.turbine)
        }

        val commonIntegrationTest = create("commonIntegrationTest") {
            dependsOn(commonTest.get())

            dependencies {
                implementation(libs.androidx.sqlite.bundled)
            }
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
        "iosSimulatorArm64",
        "tvosSimulatorArm64",
        "watchosSimulatorArm64",
    )

    targets.forEach { target ->
        val capitalized = target.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        add("ksp${capitalized}Test", libs.androidx.room.compiler)
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
