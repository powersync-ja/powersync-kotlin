import app.cash.sqldelight.core.capitalize
import com.powersync.plugins.sonatype.setupGithubRepository
import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    alias(libs.plugins.mokkery)
}

val sqliteVersion = "3450200"
val sqliteReleaseYear = "2024"

val sqliteSrcFolder =
    project.layout.buildDirectory
        .dir("native/sqlite")
        .get()

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    val destination = sqliteSrcFolder.file(zipFileName).asFile
    src("https://www.sqlite.org/$sqliteReleaseYear/$zipFileName")
    dest(destination)
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

val buildCInteropDef by tasks.registering {
    dependsOn(unzipSQLiteSources)

    val interopFolder =
        project.layout.buildDirectory
            .dir("interop/sqlite")
            .get()

    val cFile = sqliteSrcFolder.file("sqlite3.c").asFile
    val defFile = interopFolder.file("sqlite3.def").asFile

    doFirst {
        defFile.writeText(
            """
            package = com.powersync.sqlite3
            ---

            """.trimIndent() + cFile.readText(),
        )
    }
    outputs.files(defFile)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
            cinterops.create("sqlite") {
                val cInteropTask = tasks[interopProcessingTaskName]
                cInteropTask.dependsOn(buildCInteropDef)
                definitionFile =
                    buildCInteropDef
                        .get()
                        .outputs.files.singleFile
                compilerOpts.addAll(listOf("-DHAVE_GETHOSTUUID=0"))
            }
            cinterops.create("powersync-sqlite-core")
        }
    }

    explicitApi()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        commonMain.dependencies {
            implementation(libs.uuid)
            implementation(libs.kotlin.stdlib)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentnegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.io)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.stately.concurrency)
            implementation(libs.configuration.annotations)
            api(project(":persistence"))
            api(libs.kermit)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqlite.jdbc)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.kermit.test)
        }
    }
}

android {
    kotlin {
        jvmToolchain(17)
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

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DSQLITE3_SRC_DIR=${sqliteSrcFolder.asFile.absolutePath}",
                    ),
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = project.file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
}

val os = OperatingSystem.current()
val binariesAreProvided = project.findProperty("powersync.binaries.provided") == "true"
val binariesFolder =
    project.layout.buildDirectory
        .dir("binaries/desktop")

val getBinaries = if (binariesAreProvided) {
    // Binaries for all OS must be provided (manually or by the CI) in binaries/desktop

    val verifyPowersyncBinaries = tasks.register("verifyPowersyncBinaries") {
        val directory = projectDir.resolve("binaries/desktop")
        val binaries = listOf(
            directory.resolve("libpowersync-sqlite_aarch64.so"),
            directory.resolve("libpowersync-sqlite_x64.so"),
            directory.resolve("libpowersync-sqlite_aarch64.dylib"),
            directory.resolve("libpowersync-sqlite_x64.dylib"),
            directory.resolve("powersync-sqlite_x64.dll"),
        )
        doLast {
            binaries.forEach {
                if (!it.exists()) error("File $it does not exist")
                if (!it.isFile) error("File $it is not a regular file")
            }
        }
        outputs.files(*binaries.toTypedArray())
    }
    verifyPowersyncBinaries
} else {
    // Building locally for the current OS

    fun registerCMakeTasks(
        suffix: String,
        vararg defines: String,
    ): TaskProvider<Exec> {
        val cmakeConfigure = tasks.register<Exec>("cmakeJvmConfigure${suffix.capitalize()}") {
            dependsOn(unzipSQLiteSources)
            group = "cmake"
            workingDir = layout.buildDirectory.dir("cmake/$suffix").get().asFile
            inputs.files(
                "src/jvmMain/cpp",
                "src/jvmNative/cpp",
                sqliteSrcFolder,
            )
            outputs.dir(workingDir)
            executable = "cmake"
            args(listOf(file("src/jvmMain/cpp/CMakeLists.txt").absolutePath, "-DSUFFIX=$suffix", "-DCMAKE_BUILD_TYPE=Release") + defines.map { "-D$it" })
            doFirst {
                workingDir.mkdirs()
            }
        }

        val cmakeBuild = tasks.register<Exec>("cmakeJvmBuild${suffix.capitalize()}") {
            dependsOn(cmakeConfigure)
            group = "cmake"
            workingDir = layout.buildDirectory.dir("cmake/$suffix").get().asFile
            inputs.files(
                "src/jvmMain/cpp",
                "src/jvmNative/cpp",
                sqliteSrcFolder,
                workingDir,
            )
            outputs.dir(workingDir.resolve(if (os.isWindows) "output/Release" else "output"))
            executable = "cmake"
            args("--build", ".", "--config", "Release")
        }

        return cmakeBuild
    }

    val cmakeJvmBuilds = when {
        os.isMacOsX -> listOf(
            registerCMakeTasks("aarch64", "CMAKE_OSX_ARCHITECTURES=arm64"),
            registerCMakeTasks("x64", "CMAKE_OSX_ARCHITECTURES=x86_64"),
        )
        os.isLinux -> listOf(
            registerCMakeTasks("aarch64", "CMAKE_C_COMPILER=aarch64-linux-gnu-gcc", "CMAKE_CXX_COMPILER=aarch64-linux-gnu-g++"),
            registerCMakeTasks(
                "x64", "CMAKE_C_COMPILER=x86_64-linux-gnu-gcc", "CMAKE_CXX_COMPILER=x86_64-linux-gnu-g++"),
        )
        os.isWindows -> listOf(
            registerCMakeTasks("x64"),
        )
        else -> error("Unknown operating system: $os")
    }

    tasks.register<Copy>("cmakeJvmBuild") {
        dependsOn(cmakeJvmBuilds)
        group = "cmake"
        from(cmakeJvmBuilds)
        into(binariesFolder.map { it.dir("sqlite") })
    }
}

val downloadPowersyncDesktopBinaries = tasks.register<Download>("downloadPowersyncDesktopBinaries") {
    val coreVersion = libs.versions.powersync.core.get()
    val linux = listOf(
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.so",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.so",
    )
    val mac = listOf(
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.dylib",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.dylib",
    )
    val windows = listOf(
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync_x64.dll",
    )
    src(when {
        binariesAreProvided -> linux + mac + windows
        else -> when {
            os.isLinux -> linux
            os.isMacOsX -> mac
            os.isWindows -> windows
            else -> error("Unknown operating system: $os")
        }
    })
    dest(binariesFolder.map { it.dir("powersync") })
    onlyIfModified(true)
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(getBinaries, downloadPowersyncDesktopBinaries)
}

afterEvaluate {
    val buildTasks =
        tasks.matching {
            val taskName = it.name
            if (taskName.contains("Clean")) {
                return@matching false
            }
            if (taskName.contains("externalNative") || taskName.contains("CMake") || taskName.contains("generateJsonModel")) {
                return@matching true
            }
            return@matching false
        }

    buildTasks.forEach {
        it.dependsOn(buildCInteropDef)
    }
}

setupGithubRepository()
