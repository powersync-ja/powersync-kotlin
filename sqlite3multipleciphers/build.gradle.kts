import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.powersync.compile.ClangCompile
import com.powersync.compile.CreateSqliteCInterop
import com.powersync.compile.UnzipSqlite
import com.powersync.plugins.utils.powersyncTargets
import de.undercouch.gradle.tasks.download.Download
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
}

val sqlite3McVersion = "2.2.4"
val sqlite3BaseVersion = "3.50.4"

val downloadSqlite3Mc by tasks.registering(Download::class) {
    val zipFileName = "sqlite3mc-$sqlite3McVersion.zip"
    src("https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$sqlite3McVersion/sqlite3mc-$sqlite3McVersion-sqlite-$sqlite3BaseVersion-amalgamation.zip")
    dest(layout.buildDirectory.dir("downloads").map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(UnzipSqlite::class) {
    val zip = downloadSqlite3Mc.map { it.outputs.files.singleFile }
    inputs.file(zip)

    unzipSqlite(
        src = zipTree(zip),
        dir = layout.buildDirectory.dir("downloads/sqlite3mc"),
        filter = null,
    )
}

val hostManager = HostManager()

fun compileSqlite3McForKotlinNative(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val name = target.targetName
    val outputDir = layout.buildDirectory.dir("c/$name")

    val sqlite3Obj = outputDir.map { it.file("sqlite3mc.o") }
    val archive = outputDir.map { it.file("libsqlite3mc.a") }

    val compileSqlite = tasks.register("${name}CompileSqlite", ClangCompile::class) {
        dependsOn(unzipSQLiteSources)
        inputs.dir(unzipSQLiteSources.map { it.destinationDir })

        inputFile.set(unzipSQLiteSources.flatMap { it.destination.file("sqlite3mc_amalgamation.c") })
        konanTarget.set(target.konanTarget.name)
        include.set(unzipSQLiteSources.flatMap { it.destination })
        objectFile.set(sqlite3Obj)
    }

    val createStaticLibrary = tasks.register("${name}ArchiveSqlite", com.powersync.compile.CreateStaticLibrary::class) {
        inputs.file(compileSqlite.map { it.objectFile })
        objects.from(sqlite3Obj)
        staticLibrary.set(archive)
    }

    val buildCInteropDef = tasks.register("${name}CinteropSqlite", CreateSqliteCInterop::class) {
        inputs.file(createStaticLibrary.map { it.staticLibrary })

        archiveFile.set(archive)
        definitionFile.fileProvider(archive.map { File(it.asFile.parentFile, "sqlite3mc.def") })
    }

    return buildCInteropDef
}

kotlin {
    powersyncTargets()

    applyDefaultHierarchyTemplate()
    explicitApi()

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.cinterop.BetaInteropApi")
                optIn("com.powersync.PowerSyncInternal")
            }
        }

        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }

        androidMain {
            dependsOn(jvmAndroidMain)
        }

        jvmMain {
            dependsOn(jvmAndroidMain)
        }

        commonMain.dependencies {
            api(projects.common)
            implementation(libs.androidx.sqlite.sqlite)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            api(libs.test.kotest.assertions)
        }
    }

    targets.withType<KotlinNativeTarget> {
        if (hostManager.isEnabled(konanTarget)) {
            val compileSqlite3 = compileSqlite3McForKotlinNative(this)

            compilations.named("main") {
                cinterops.create("sqlite3mc") {
                    definitionFile.set(compileSqlite3.flatMap { it.definitionFile })
                }
            }
        }
    }
}

android {
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "com.powersync.encryption"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
//        consumerProguardFiles("proguard-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path("src/jni/CMakeLists.txt")
        }
    }

    ndkVersion = "27.1.12297006"
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(unzipSQLiteSources)
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from("build/jni-build/")
}

val xCodeInstallation = ClangCompile.resolveXcode(providers)

// Tasks to build the JNI shared library for multiple operating systems.
// Since the JNI sources rarely change, we don't run these tasks on every build. Instead,
// we'll publish these sources as one-off releases when needed, and then reference that URL.
enum class JniTarget {
    LINUX_ARM,
    LINUX_X64,
    MACOS_ARM,
    MACOS_X64,
    WINDOWS_ARM,
    WINDOWS_X64,
}

fun Exec.registerCompileOnHostTask(target: JniTarget, clang: String = "clang", toolchain: String? = null) {
    val outputDirectory = layout.buildDirectory.dir("jni-build")
    val outputFile = outputDirectory.map {
        it.file(when (target) {
            JniTarget.LINUX_ARM -> "libsqlite3mc_jni_aarch64.linux.so"
            JniTarget.LINUX_X64 -> "libsqlite3mc_jni_x64.linux.so"
            JniTarget.MACOS_ARM -> "libsqlite3mc_jni_aarch64.macos.dylib"
            JniTarget.MACOS_X64 -> "libsqlite3mc_jni_x64.macos.dylib"
            JniTarget.WINDOWS_ARM -> "sqlite3mc_jni_aarch64.dll"
            JniTarget.WINDOWS_X64 -> "sqlite3mc_jni_x64.dll"
        })
    }
    outputs.file(outputFile)

    dependsOn(unzipSQLiteSources)
    val sqlite3McSources = unzipSQLiteSources.map { it.destinationDir }
    inputs.dir(sqlite3McSources)

    inputs.dir(layout.projectDirectory.dir("src/jni/"))

    doFirst {
        outputDirectory.get().asFile.mkdirs()
    }

    if (target == JniTarget.LINUX_X64 || target == JniTarget.LINUX_ARM) {
        executable = "/opt/homebrew/bin/docker"
        args(
            "run",
            "-v", "./src:/src",
            "-v", "./build:/build",
            "powersync_kotlin_sqlite3mc_build_helper",
            "clang",
            "-fuse-ld=lld"
        )
    } else {
        executable = clang
    }

    val outputFilePath = outputFile.get().asFile.toRelativeString(project.projectDir)
    val sourceRoot = sqlite3McSources.get().toRelativeString(project.projectDir)
    val amalgamation = File(sourceRoot, "sqlite3mc_amalgamation.c").path

    args(
        "-shared",
        "-fPIC",
        when (target) {
            JniTarget.LINUX_ARM -> "--target=aarch64-pc-linux"
            JniTarget.LINUX_X64 -> "--target=x86_64-pc-linux"
            JniTarget.MACOS_ARM -> "--target=aarch64-apple-macos"
            JniTarget.MACOS_X64 -> "--target=x86_64-apple-macos"
            JniTarget.WINDOWS_ARM -> "--target=aarch64-w64-mingw32uwp"
            JniTarget.WINDOWS_X64 -> "--target=x86_64-w64-mingw32uwp"
        },
        "-o",
        outputFilePath,
        "src/jni/sqlite_bindings.cpp",
        amalgamation,
        "-I",
        sourceRoot,
        "-I",
        "src/jni/headers/common",
        "-I",
        when (target) {
            JniTarget.LINUX_X64, JniTarget.LINUX_ARM -> "src/jni/headers/inc_linux"
            JniTarget.MACOS_X64, JniTarget.MACOS_ARM -> "src/jni/headers/inc_mac"
            JniTarget.WINDOWS_X64, JniTarget.WINDOWS_ARM -> "src/jni/headers/inc_win"
        },
        "-O3",
        *ClangCompile.sqlite3ClangOptions,
    )

    toolchain?.let { args.add(it) }
}

fun registerCompileMacOsHostTask(arm: Boolean): TaskProvider<Exec> {
    val architecture = if (arm) JniTarget.MACOS_ARM else JniTarget.MACOS_X64

    return tasks.register<Exec>("jniCompile${architecture.name}") {
        val xcode = Path(xCodeInstallation.get())
        val toolchain =
            xcode.resolve("Toolchains/XcodeDefault.xctoolchain/usr/bin").absolutePathString()
        registerCompileOnHostTask(architecture, toolchain = toolchain)
    }
}

fun registerCompileWindowsOnMacOsTask(arm: Boolean): TaskProvider<Exec> {
    val architecture = if (arm) JniTarget.WINDOWS_ARM else JniTarget.WINDOWS_X64

    return tasks.register<Exec>("jniCompile${architecture.name}") {
        registerCompileOnHostTask(architecture, clang = "/Users/simon/Downloads/llvm-mingw-20251104-ucrt-macos-universal/bin/clang")
    }
}

fun registerCompileLinuxOnMacOsTask(arm: Boolean): TaskProvider<Exec> {
    val architecture = if (arm) JniTarget.LINUX_ARM else JniTarget.LINUX_X64

    return tasks.register<Exec>("jniCompile${architecture.name}") {
        registerCompileOnHostTask(architecture)
    }
}

val linuxArm64 = registerCompileLinuxOnMacOsTask(true)
val linuxX64 = registerCompileLinuxOnMacOsTask(false)

val macosArm64 = registerCompileMacOsHostTask(true)
val macosX64 = registerCompileMacOsHostTask(false)

val windowsArm64 = registerCompileWindowsOnMacOsTask(true)
val windowsX64 = registerCompileWindowsOnMacOsTask(false)

tasks.register("jniCompile") {
    dependsOn(linuxX64, linuxArm64)
    dependsOn(macosX64, macosArm64)
    dependsOn(windowsX64, macosArm64)
}
