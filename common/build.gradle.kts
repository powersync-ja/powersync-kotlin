import com.powersync.plugins.utils.powersyncTargets
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.konan.target.Family
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kotlin.atomicfu)
    id("dokka-convention")
}

val binariesFolder = project.layout.buildDirectory.dir("binaries/desktop")
val downloadPowersyncDesktopBinaries by tasks.registering(Download::class) {
    description = "Download PowerSync core extensions for JVM builds and releases"

    val coreVersion =
        libs.versions.powersync.core
            .get()
    val linux_aarch64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.so"
    val linux_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.so"
    val macos_aarch64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.dylib"
    val macos_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.dylib"
    val windows_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync_x64.dll"

    val includeAllPlatformsForJvmBuild =
        project.findProperty("powersync.binaries.allPlatforms") == "true"
    val os = OperatingSystem.current()

    // The jar we're releasing for JVM clients needs to include the core extension. For local tests, it's enough to only
    // download the extension for the OS running the build. For releases, we want to include them all.
    // We're not compiling native code for JVM builds here (we're doing that for Android only), so we just have to
    // fetch prebuilt binaries from the powersync-sqlite-core repository.
    if (includeAllPlatformsForJvmBuild) {
        src(listOf(linux_aarch64, linux_x64, macos_aarch64, macos_x64, windows_x64))
    } else {
        val (aarch64, x64) =
            when {
                os.isLinux -> linux_aarch64 to linux_x64
                os.isMacOsX -> macos_aarch64 to macos_x64
                os.isWindows -> null to windows_x64
                else -> error("Unknown operating system: $os")
            }
        val arch = System.getProperty("os.arch")
        src(
            when (arch) {
                "aarch64" -> listOfNotNull(aarch64)
                "amd64", "x86_64" -> listOfNotNull(x64)
                else -> error("Unsupported architecture: $arch")
            },
        )
    }
    dest(binariesFolder.map { it.dir("powersync") })
    onlyIfModified(true)
}

val generateVersionConstant by tasks.registering {
    val target = project.layout.buildDirectory.dir("generated/constants")
    val packageName = "com.powersync.build"

    outputs.dir(target)
    val currentVersion = version.toString()

    doLast {
        val dir = target.get().asFile
        dir.mkdir()
        val rootPath = dir.toPath()

        val source =
            """
            package $packageName
            
            internal const val LIBRARY_VERSION: String = "$currentVersion"

            """.trimIndent()

        val packageRoot = packageName.split('.').fold(rootPath, Path::resolve)
        packageRoot.createDirectories()

        packageRoot.resolve("BuildConstants.kt").writeText(source)
    }
}

kotlin {
    powersyncTargets()

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }

            if (target.konanTarget.family == Family.WATCHOS) {
                // We're linking the core extension statically, which means that we need a cinterop
                // to call powersync_init_static
                cinterops.create("powersync_static") {
                    packageName("com.powersync.static")
                    headers(file("src/watchosMain/powersync_static.h"))
                }
            }

            cinterops.create("sqlite3") {
                packageName("com.powersync.internal.sqlite3")
                includeDirs.allHeaders("src/nativeMain/interop/")
                definitionFile.set(project.file("src/nativeMain/interop/sqlite3.def"))
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

        val commonJava by creating {
            dependsOn(commonMain.get())
        }

        commonMain.configure {
            kotlin {
                srcDir(generateVersionConstant)
            }

            dependencies {
                api(libs.androidx.sqlite.sqlite)

                implementation(libs.uuid)
                implementation(libs.kotlin.stdlib)
                implementation(libs.ktor.client.contentnegotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.kotlinx.io)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.stately.concurrency)
                implementation(libs.configuration.annotations)
                api(libs.ktor.client.core)
                api(libs.kermit)
            }
        }

        androidMain {
            dependsOn(commonJava)
            dependencies {
                api(libs.powersync.sqlite.core.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        jvmMain {
            dependsOn(commonJava)

            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.sqlite.bundled)
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

        // Common apple targets where we link the core extension dynamically
        val appleNonWatchOsMain by creating {
            dependsOn(appleMain.get())
        }

        macosMain.orNull?.dependsOn(appleNonWatchOsMain)
        iosMain.orNull?.dependsOn(appleNonWatchOsMain)
        tvosMain.orNull?.dependsOn(appleNonWatchOsMain)

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.test.turbine)
            implementation(libs.test.kotest.assertions)
            implementation(libs.kermit.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.test.turbine)
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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    namespace = "com.powersync"
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

    ndkVersion = "27.1.12297006"
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(downloadPowersyncDesktopBinaries)
}

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
    moduleName.set("PowerSync Common")
}
