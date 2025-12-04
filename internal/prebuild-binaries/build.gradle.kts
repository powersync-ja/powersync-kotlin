import com.powersync.compile.ClangCompile
import com.powersync.compile.UnzipSqlite
import de.undercouch.gradle.tasks.download.Download
import kotlin.io.path.Path
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.konan.target.KonanTarget
import com.powersync.compile.CreateStaticLibrary
import com.powersync.compile.JniLibraryCompile
import com.powersync.compile.JniTarget
import kotlin.io.path.absolutePathString

plugins {
    alias(libs.plugins.downloadPlugin)
}

val sqlite3McVersion = "2.2.6"
val sqlite3BaseVersion = "3.51.1"
val sqlite3ReleaseYear = "2025"
val sqlite3ExpandedVersion = "3510100"

data class CompiledAsset(
    val output: Provider<RegularFileProperty>,
    val fullName: String,
)

val xCodeInstallation = ClangCompile.resolveXcode(providers)

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqlite3ExpandedVersion.zip"
    src("https://www.sqlite.org/$sqlite3ReleaseYear/$zipFileName")
    dest(layout.buildDirectory.dir("downloads").map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val downloadSqlite3MultipleCipherSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite3mc-$sqlite3McVersion.zip"
    src("https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$sqlite3McVersion/sqlite3mc-$sqlite3McVersion-sqlite-$sqlite3BaseVersion-amalgamation.zip")
    dest(layout.buildDirectory.dir("downloads").map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(UnzipSqlite::class) {
    val zip = downloadSQLiteSources.map { it.outputs.files.singleFile }
    inputs.file(zip)

    unzipSqlite(
        src = zipTree(zip),
        dir = layout.buildDirectory.dir("downloads/sqlite3")
    )
}

val unzipSqlite3MultipleCipherSources by tasks.registering(UnzipSqlite::class) {
    val zip = downloadSqlite3MultipleCipherSources.map { it.outputs.files.singleFile }
    inputs.file(zip)

    unzipSqlite(
        src = zipTree(zip),
        dir = layout.buildDirectory.dir("downloads/sqlite3mc"),
        filter = null,
    )
}

val prepareAndroidBuild by tasks.registering(Copy::class) {
    from(unzipSqlite3MultipleCipherSources.flatMap { it.destination }) {
        include(
            "sqlite3mc_amalgamation.c",
            "sqlite3mc_amalgamation.h",
            "sqlite3.h"
        )
    }

    from("jni/CMakeLists.txt")
    from("jni/sqlite_bindings.cpp")
    into(layout.buildDirectory.dir("android"))
}

fun compileJni(target: JniTarget): CompiledAsset {
    val name = target.filename("sqlite3mc_jni")

    val task = tasks.register<JniLibraryCompile>("compile${target.name}") {
        this.target.set(target)
        inputFiles.from(
            "jni/sqlite_bindings.cpp",
            unzipSqlite3MultipleCipherSources.flatMap { it.destination.file("sqlite3mc_amalgamation.c") }
        )
        include.set(unzipSqlite3MultipleCipherSources.flatMap { it.destination })
        sharedLibrary.set(layout.buildDirectory.file("jni/$name"))

        when (target) {
            JniTarget.LINUX_X64, JniTarget.LINUX_ARM -> {}
            JniTarget.WINDOWS_X64, JniTarget.WINDOWS_ARM -> {
                // For Windows, we compile with LLVM MinGW: https://github.com/mstorsjo/llvm-mingw
                val clang = layout.buildDirectory.file("llvm-mingw/bin/clang").map { it.asFile.path }
                clangPath.set(clang)
            }
            JniTarget.MACOS_X64, JniTarget.MACOS_ARM -> {
                // on macOS: Compile with xcode tools
                toolchain.set(xCodeInstallation.map {
                    val xcode = Path(it)
                    xcode.resolve("Toolchains/XcodeDefault.xctoolchain/usr/bin").absolutePathString()
                })
            }
        }
    }

    return CompiledAsset(
        output = task.map { it.sharedLibrary },
        fullName = name
    )
}

fun compileSqliteForKotlinNativeOnApple(library: String, abi: String): TaskProvider<CreateStaticLibrary> {
    val name = "$library$abi"
    val outputDir = layout.buildDirectory.dir("c/$abi")

    val sqlite3Obj = outputDir.map { it.file("$library.o") }
    val archive = outputDir.map { it.file("lib$library.a") }

    val compileSqlite = tasks.register("${name}CompileSqlite", ClangCompile::class) {
        val (sourceTask, filename) = if (library == "sqlite3") {
            unzipSQLiteSources to "sqlite3.c"
        } else {
            unzipSqlite3MultipleCipherSources to "sqlite3mc_amalgamation.c"
        }

        inputs.dir(sourceTask.map { it.destination })
        include.set(sourceTask.flatMap { it.destination })
        inputFile.set(sourceTask.flatMap { it.destination.file(filename) })

        konanTarget.set(abi)
        objectFile.set(sqlite3Obj)
    }

    val createStaticLibrary = tasks.register("${name}ArchiveSqlite", CreateStaticLibrary::class) {
        inputs.file(compileSqlite.map { it.objectFile })
        objects.from(sqlite3Obj)
        staticLibrary.set(archive)
    }

    return createStaticLibrary
}

val kotlinNativeCompileTasks = buildList {
    val targets = KonanTarget.predefinedTargets.values.filter { it.family.isAppleFamily }.map { it.name }.toList()
    for (library in listOf("sqlite3", "sqlite3mc")) {
        for (abi in targets) {
            val task = compileSqliteForKotlinNativeOnApple(library, abi)
            val output = task.map { it.staticLibrary }
            val fullName = "$abi$library.a"

            add(CompiledAsset(output, fullName))
        }
    }
}

val compileNative by tasks.registering(Copy::class) {
    into(project.layout.buildDirectory.dir("output/static"))

    for (task in kotlinNativeCompileTasks) {
        from(task.output) {
            rename { task.fullName }
        }
    }
}

val jniCompileTasks: Map<JniTarget, CompiledAsset> = buildMap {
    for (target in JniTarget.entries) {
        put(target, compileJni(target))
    }
}

val compileJni by tasks.registering(Copy::class) {
    into(project.layout.buildDirectory.dir("output/jni"))

    for (task in jniCompileTasks.values) {
        from(task.output) {
            rename { task.fullName }
        }
    }
}

val compileAll by tasks.registering {
    dependsOn(compileNative)
    dependsOn(compileJni)
}

val hasPrebuiltAssets = providers.gradleProperty("hasPrebuiltAssets").map { it.toBooleanStrict() }

val nativeSqliteConfiguration by configurations.creating {
    isCanBeResolved = false
}
val jniSqlite3McConfiguration by configurations.creating {
    isCanBeResolved = false
}
val androidBuildSourceConfiguration by configurations.creating {
    // We share the downloaded sqlite3mc sources with the sqlite3multipleciphers project, which uses it to
    // setup a cmake-based NDK build. Since these work on all platforms and only run when needed, there's no
    // need to prebuild them.
    isCanBeResolved = false
}

artifacts {
    if (hasPrebuiltAssets.getOrElse(false)) {
        // In CI builds, we set hasPrebuiltAssets=true. In that case, contents of build/output have been downloaded from
        // cache and don't need to be rebuilt.
        add(nativeSqliteConfiguration.name, layout.buildDirectory.dir("output/static"))
        add(jniSqlite3McConfiguration.name, layout.buildDirectory.dir("output/jni"))
    } else {
        add(nativeSqliteConfiguration.name, compileNative)
        add(jniSqlite3McConfiguration.name, compileJni)
    }

    add(androidBuildSourceConfiguration.name, prepareAndroidBuild)
}
