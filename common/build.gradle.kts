import com.powersync.compile.CreatePowerSyncSqliteCoreCInterop
import com.powersync.plugins.utils.powersyncTargets
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    alias(libs.plugins.mokkery)
    id("dokka-convention")
}

val powersyncStaticLibrariesConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}

val binariesFolder = project.layout.buildDirectory.dir("binaries/desktop")
val downloadPowersyncDesktopBinaries by tasks.registering(Download::class) {
    description = "Download PowerSync core extensions for JVM builds and releases"

    val coreVersion =
        libs.versions.powersync.core
            .get()

    fun downloadUrl(filename: String): String {
        return "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/$filename"
    }

    val linux_aarch64 = "libpowersync_aarch64.linux.so"
    val linux_x64 = "libpowersync_x64.linux.so"
    val macos_aarch64 = "libpowersync_aarch64.macos.dylib"
    val macos_x64 = "libpowersync_x64.macos.dylib"
    val windows_aarch64 = "powersync_aarch64.dll"
    val windows_x64 = "powersync_x64.dll"

    val includeAllPlatformsForJvmBuild =
        project.findProperty("powersync.binaries.allPlatforms") == "true"
    val os = OperatingSystem.current()

    // The jar we're releasing for JVM clients needs to include the core extension. For local tests, it's enough to only
    // download the extension for the OS running the build. For releases, we want to include them all.
    // We're not compiling native code for JVM builds here, so we just have to fetch prebuilt binaries from the
    // powersync-sqlite-core repository.
    if (includeAllPlatformsForJvmBuild) {
        val allSources = sequenceOf(linux_aarch64, linux_x64, macos_aarch64, macos_x64, windows_aarch64, windows_x64)
            .map(::downloadUrl)
            .toList()
        src(allSources)
    } else {
        val (aarch64, x64) =
            when {
                os.isLinux -> linux_aarch64 to linux_x64
                os.isMacOsX -> macos_aarch64 to macos_x64
                os.isWindows -> windows_aarch64 to windows_x64
                else -> error("Unknown operating system: $os")
            }
        val arch = System.getProperty("os.arch")
        val filename = when (arch) {
            "aarch64" -> aarch64
            "amd64", "x86_64" -> x64
            else -> error("Unsupported architecture: $arch")
        }
        src(downloadUrl(filename))
    }
    dest(binariesFolder.map { it.dir("powersync") })
    onlyIfModified(true)
}

dependencies {
    powersyncStaticLibrariesConfiguration(project(path=":internal:download-core-extension", configuration="powersyncStaticLibrariesConfiguration"))
}

fun linkCoreExtensionStatically(target: KotlinNativeTarget): TaskProvider<CreatePowerSyncSqliteCoreCInterop> {
    val buildCInteropDef = tasks.register("${target.name}CinteropCoreExtension", CreatePowerSyncSqliteCoreCInterop::class) {
        val precompiledSqlite: FileCollection = powersyncStaticLibrariesConfiguration
        inputs.files(precompiledSqlite)
        dependsOn(precompiledSqlite)

        val fileName = when (val konanTarget = target.konanTarget) {
            KonanTarget.LINUX_ARM64 -> "libpowersync_aarch64.linux.a"
            KonanTarget.LINUX_X64 -> "libpowersync_x64.linux.a"

            KonanTarget.MINGW_X64 -> "powersync_x64.lib"

            KonanTarget.MACOS_ARM64 -> "libpowersync_aarch64.macos.a"
            KonanTarget.MACOS_X64 -> "libpowersync_x64.macos.a"

            KonanTarget.IOS_ARM64 -> "libpowersync_aarch64.ios.a"
            KonanTarget.IOS_SIMULATOR_ARM64 -> "libpowersync_aarch64.ios-sim.a"
            KonanTarget.IOS_X64 -> "libpowersync_x64.ios-sim.a"

            KonanTarget.TVOS_ARM64 -> "libpowersync_aarch64.tvos.a"
            KonanTarget.TVOS_SIMULATOR_ARM64 -> "libpowersync_aarch64.tvos-sim.a"
            KonanTarget.TVOS_X64 -> "libpowersync_x64.tvos-sim.a"

            KonanTarget.WATCHOS_ARM64 -> "libpowersync_arm64_32.watchos.a"
            KonanTarget.WATCHOS_DEVICE_ARM64 -> "libpowersync_aarch64.watchos.a"
            KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "libpowersync_aarch64.watchos-sim.a"
            KonanTarget.WATCHOS_X64 -> "libpowersync_x64.watchos-sim.a"
            else -> error("Unsupported target: $konanTarget")
        }

        val staticLibrary = precompiledSqlite.singleFile.resolve(fileName)
        archiveFile.set(staticLibrary)
        definitionFile.value(layout.buildDirectory.map { it.file("interopDefs/${target.name}/sqlite3mc.def") })
    }

    return buildCInteropDef
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
    powersyncTargets(
        android = {
            namespace = "com.powersync.common"
        }
    )

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }

            cinterops.create("core_extension") {
                val interopSource = linkCoreExtensionStatically(this@withType)
                definitionFile.set(interopSource.flatMap { it.definitionFile })
            }

            cinterops.create("sqlite3") {
                // We're not linking SQLite here (to allow using this package with e.g. SQLCipher or
                // SQLite3MultipleCiphers), :core depends on this package and is responsible for bundling SQLite.
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
                optIn("com.powersync.PowerSyncInternal")
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
                implementation(libs.rsocket.core)
                implementation(libs.rsocket.transport.websocket)
                api(libs.ktor.client.core)
                api(libs.kermit)
            }
        }

        androidMain {
            dependsOn(commonJava)
            dependencies {
                api(libs.powersync.sqlite.core.android)
            }
        }

        jvmMain {
            dependsOn(commonJava)
        }

        commonTest.dependencies {
            implementation(projects.internal.testutils)
            implementation(libs.kotlin.test)
        }

        // We're putting the native libraries into our JAR, so integration tests for the JVM can run as part of the unit
        // tests.
        jvmTest {
            dependsOn(commonIntegrationTest)
        }

        // We have special setup in this build configuration to make these tests link the PowerSync extension, so they
        // can run integration tests along with the executable for unit testing.
        appleTest.orNull?.dependsOn(commonIntegrationTest)
    }
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(downloadPowersyncDesktopBinaries)
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
    moduleName.set("PowerSync Common")
}
