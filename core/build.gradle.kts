import com.powersync.plugins.utils.powersyncTargets
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    alias(libs.plugins.mokkery)
    id("dokka-convention")
}

kotlin {
    powersyncTargets()

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

    explicitApi()

    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.experimental.ExperimentalObjCRefinement")
            }
        }

        val commonIntegrationTest by creating {
            dependsOn(commonTest.get())
        }

        commonMain.configure {
            dependencies {
                api(projects.common)
            }
        }

        androidMain {
            dependencies {
                api(libs.powersync.sqlite.core.android)
                api(libs.androidx.sqlite.bundled)
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                api(libs.androidx.sqlite.bundled)
                implementation(libs.ktor.client.okhttp)
            }
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)

            // We're not using the bundled SQLite library for Apple platforms. Instead, we depend on
            // static-sqlite-driver to link SQLite and have our own bindings implementing the
            // driver. The reason for this is that androidx.sqlite-bundled causes linker errors for
            // our Swift SDK.
            implementation(projects.staticSqliteDriver)
        }

        commonTest.dependencies {
            implementation(projects.internal.testutils)
            implementation(libs.kotlin.test)
        }

        // We're putting the native libraries into our JAR, so integration tests for the JVM can run as part of the unit
        // tests.
        jvmTest.get().dependsOn(commonIntegrationTest)

        // We have special setup in this build configuration to make these tests link the PowerSync extension, so they
        // can run integration tests along with the executable for unit testing.
        appleTest.orNull?.dependsOn(commonIntegrationTest)
    }
}

android {
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "com.powersync.core"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        consumerProguardFiles("proguard-rules.pro")
    }
}

// We want to build with recent JDKs, but need to make sure we support Java 8. https://jakewharton.com/build-on-latest-java-test-through-lowest-java/
val testWithJava8 by tasks.registering(KotlinJvmTest::class) {
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
        }

    description = "Run tests with Java 8"
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    // Copy inputs from the normal test task
    val testTask = tasks.getByName("jvmTest") as KotlinJvmTest
    classpath = testTask.classpath
    testClassesDirs = testTask.testClassesDirs
}
tasks.named("check").configure { dependsOn(testWithJava8) }

tasks.withType<KotlinTest> {
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStandardStreams = true
        showStackTraces = true
    }
}

dokka {
    moduleName.set("PowerSync")
}
