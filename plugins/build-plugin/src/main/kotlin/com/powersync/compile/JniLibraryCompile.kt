package com.powersync.compile

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.ExecException
import java.io.File
import javax.inject.Inject

enum class JniTarget {
    LINUX_ARM,
    LINUX_X64,
    MACOS_ARM,
    MACOS_X64,
    WINDOWS_ARM,
    WINDOWS_X64;

    fun filename(library: String): String = when (this) {
        LINUX_ARM -> "lib${library}_aarch64.linux.so"
        LINUX_X64 -> "lib${library}_x64.linux.so"
        MACOS_ARM -> "lib${library}_aarch64.macos.dylib"
        MACOS_X64 -> "lib${library}_x64.macos.dylib"
        WINDOWS_ARM -> "${library}_aarch64.dll"
        WINDOWS_X64 -> "${library}_x64.dll"
    }
}

/**
 * A task compiling the native Androidx SQLite JNI library on macOS.
 *
 * We use this custom task instead of the regular library to be able to swap out sqlite3 for SQLiteMultipleCiphers.
 */
@CacheableTask
abstract class JniLibraryCompile: DefaultTask() {
    @get:Input
    abstract val target: Property<JniTarget>

    @get:Input
    abstract val clangPath: Property<String>

    @get:Input
    @get:Optional
    abstract val toolchain: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val include: DirectoryProperty

    @get:OutputFile
    abstract val sharedLibrary: RegularFileProperty

    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:Inject
    protected abstract val layout: ProjectLayout

    init {
        clangPath.convention("clang")
    }

    @TaskAction
    fun run() {
        val target = this.target.get()
        val clang = clangPath.getOrElse("clang")
        val workingDir = layout.projectDirectory.asFile

        fun filePath(file: File): String {
            return file.relativeTo(workingDir).path
        }

        val args = buildList {
            if (target == JniTarget.LINUX_X64) {
                add("-fuse-ld=lld")
                add("--sysroot=build/sysroot/")
            }

            if (target == JniTarget.LINUX_ARM) {
                val gccParent = layout.buildDirectory.dir("sysroot/usr/lib/gcc/aarch64-linux-gnu")
                val gccPath = filePath(gccParent.get().asFile.listFiles().single())

                add("-fuse-ld=lld")
                add("--sysroot=build/sysroot/usr/aarch64-linux-gnu/")
                add("-Wl,-L")
                add(gccPath)
                add("-B")
                add(gccPath)
            }

            toolchain.orNull?.let { add("-B$toolchain") }

            add("-shared")
            add("-fPIC")
            add(when (target) {
                JniTarget.LINUX_ARM -> "--target=aarch64-pc-linux"
                JniTarget.LINUX_X64 -> "--target=x86_64-pc-linux"
                JniTarget.MACOS_ARM -> "--target=aarch64-apple-macos"
                JniTarget.MACOS_X64 -> "--target=x86_64-apple-macos"
                JniTarget.WINDOWS_ARM -> "--target=aarch64-w64-mingw32uwp"
                JniTarget.WINDOWS_X64 -> "--target=x86_64-w64-mingw32uwp"
            })
            add("-o")
            add(filePath(sharedLibrary.get().asFile))
            inputFiles.files.forEach { add(filePath(it)) }
            add("-I")
            add(filePath(include.get().asFile))
            add("-I")
            add("jni/headers/common")
            add("-I")
            add(when (target) {
                JniTarget.LINUX_X64, JniTarget.LINUX_ARM -> "jni/headers/inc_linux"
                JniTarget.MACOS_X64, JniTarget.MACOS_ARM -> "jni/headers/inc_mac"
                JniTarget.WINDOWS_X64, JniTarget.WINDOWS_ARM -> "jni/headers/inc_win"
            })
            add("-O3")
            addAll(ClangCompile.sqlite3ClangOptions)
        }

        val execProvider = providers.exec {
            this.workingDir = workingDir
            executable = clang
            args(args)

            isIgnoreExitValue = true // So that we can provide better error messages
        }

        try {
            execProvider.result.get().assertNormalExitValue()
        } catch (_: ProcessExecutionException) {
            val formattedArgs = args.joinToString(separator = " ") { "\"$it\"" }
            val stderr = execProvider.standardError.asText.orNull

            throw ProcessExecutionException("Could not start $clang $formattedArgs. Stderr: $stderr")
        }
    }
}
