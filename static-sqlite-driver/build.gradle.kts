import java.io.File
import java.io.FileInputStream
import com.powersync.plugins.sonatype.setupGithubRepository
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.PlatformManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.downloadPlugin)
    id("com.powersync.plugins.sonatype")
}

val sqliteVersion = "3450200"
val sqliteReleaseYear = "2024"

setupGithubRepository()

val downloads = layout.buildDirectory.dir("downloads")
val sqliteSrcFolder = downloads.map { it.dir("sqlite3") }

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    src("https://www.sqlite.org/$sqliteReleaseYear/$zipFileName")
    dest(downloads.map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(Copy::class) {
    dependsOn(downloadSQLiteSources)

    from(
        zipTree(downloadSQLiteSources.get().dest).matching {
            include("*/sqlite3.*")
            exclude {
                it.isDirectory
            }
            eachFile {
                this.path = this.name
            }
        },
    )
    into(sqliteSrcFolder)
}

// Obtain host and platform manager from Kotlin multiplatform plugin. They're supposed to be
// internal, but it's very convenient to have them because they expose the necessary toolchains we
// use to compile SQLite for the platforms we need.
val hostManager = HostManager()

fun compileSqlite(target: KotlinNativeTarget): TaskProvider<Task> {
    val name = target.targetName
    val outputDir = layout.buildDirectory.dir("c/$name")

    val compileSqlite = tasks.register("${name}CompileSqlite") {
        dependsOn(unzipSQLiteSources)
        val targetDirectory = outputDir.get()
        val sqliteSource = sqliteSrcFolder.map { it.file("sqlite3.c") }.get()
        val output = targetDirectory.file("sqlite3.o")

        inputs.file(sqliteSource)
        outputs.file(output)

        doFirst {
            targetDirectory.asFile.mkdirs()
            output.asFile.delete()
        }

        doLast {
            val (llvmTarget, sysRoot) = when (target.konanTarget) {
                KonanTarget.IOS_X64 -> "x86_64-apple-ios12.0-simulator" to "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
                KonanTarget.IOS_ARM64 -> "arm64-apple-ios12.0" to "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"
                KonanTarget.IOS_SIMULATOR_ARM64 -> "arm64-apple-ios14.0-simulator" to "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
                else -> error("Unexpected target $target")
            }

            providers.exec {
                executable = "clang"
                args(
                    "-B/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin",
                    "-fno-stack-protector",
                    "-target",
                    llvmTarget,
                    "-isysroot",
                    sysRoot,
                    "-fPIC",
                    "--compile",
                    "-I${sqliteSrcFolder.get().asFile.absolutePath}",
                    sqliteSource.asFile.absolutePath,
                    "-DHAVE_GETHOSTUUID=0",
                    "-DSQLITE_ENABLE_DBSTAT_VTAB",
                    "-DSQLITE_ENABLE_FTS5",
                    "-DSQLITE_ENABLE_RTREE",
                    "-O3",
                    "-o",
                    "sqlite3.o",
                )

                workingDir = targetDirectory.asFile
            }.result.get()
        }
    }

    val createStaticLibrary = tasks.register("${name}ArchiveSqlite") {
        dependsOn(compileSqlite)
        val targetDirectory = outputDir.get()
        inputs.file(targetDirectory.file("sqlite3.o"))
        outputs.file(targetDirectory.file("libsqlite3.a"))

        doLast {
            providers.exec {
                executable = "ar"
                args("rc", "libsqlite3.a", "sqlite3.o")

                workingDir = targetDirectory.asFile
            }.result.get()
        }
    }

    val buildCInteropDef = tasks.register("${name}CinteropSqlite") {
        dependsOn(createStaticLibrary)

        val archive = createStaticLibrary.get().outputs.files.singleFile
        inputs.file(archive)

        val parent = archive.parentFile
        val defFile = File(parent, "sqlite3.def")
        outputs.file(defFile)

        doFirst {
            defFile.writeText(
                """
            package = com.powersync.sqlite3
            
            linkerOpts.linux_x64 = -lpthread -ldl
            linkerOpts.macos_x64 = -lpthread -ldl
            staticLibraries=${archive.name}
            libraryPaths=${parent.relativeTo(project.layout.projectDirectory.asFile.canonicalFile)}
            """.trimIndent(),
            )
        }
    }

    return buildCInteropDef
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.cinterop.BetaInteropApi")
            }
        }

        nativeTest {
            dependencies {
                implementation(libs.sqliter)
            }
        }
    }

    targets.withType<KotlinNativeTarget> {
        if (hostManager.isEnabled(konanTarget)) {
            val compileSqlite3 = compileSqlite(this)

            compilations.named("main") {
                cinterops.create("sqlite3") {
                    val cInteropTask = tasks[interopProcessingTaskName]
                    cInteropTask.dependsOn(compileSqlite3)
                    definitionFile = compileSqlite3.get().outputs.files.singleFile
                    includeDirs(sqliteSrcFolder.get())
                }
            }
        }
    }
}
