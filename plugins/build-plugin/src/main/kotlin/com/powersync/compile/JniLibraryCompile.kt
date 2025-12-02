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

        providers.exec {
            workingDir = layout.projectDirectory.asFile

            fun filePath(file: File): String {
                return file.relativeTo(workingDir).path
            }

            if (target == JniTarget.LINUX_X64 || target == JniTarget.LINUX_ARM) {
                executable = "/opt/homebrew/bin/docker"
                args(
                    "run",
                    "-v", "./jni:/jni",
                    "-v", "./build:/build",
                    "powersync_kotlin_sqlite3mc_build_helper",
                    "clang",
                    "-fuse-ld=lld"
                )
            } else {
                executable = clang
            }

            toolchain.orNull?.let { args("-B$toolchain") }

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
                filePath(sharedLibrary.get().asFile),
                *inputFiles.files.map { filePath(it) }.toTypedArray(),
                "-I",
                filePath(include.get().asFile),
                "-I",
                "jni/headers/common",
                "-I",
                when (target) {
                    JniTarget.LINUX_X64, JniTarget.LINUX_ARM -> "jni/headers/inc_linux"
                    JniTarget.MACOS_X64, JniTarget.MACOS_ARM -> "jni/headers/inc_mac"
                    JniTarget.WINDOWS_X64, JniTarget.WINDOWS_ARM -> "jni/headers/inc_win"
                },
                "-O3",
                *ClangCompile.sqlite3ClangOptions,
            )
        }.result.get()
    }
}
