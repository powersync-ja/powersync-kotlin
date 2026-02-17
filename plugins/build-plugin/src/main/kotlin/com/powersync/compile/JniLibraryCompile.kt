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
        val compiler = if (target == JniTarget.LINUX_ARM || target == JniTarget.LINUX_X64) {
            // We only compile Linux libraries on Linux hosts, and use GCC for that. The reason is
            // that obtaining sysroots for Linux on Apple platforms is kind of annoying.
            GccLibraryCompiler(target)
        } else {
            ClangLibraryCompiler(target)
        }

        compiler.run()
    }

    private abstract inner class SharedLibraryCompiler(protected val target: JniTarget) {
        private val workingDir = layout.projectDirectory.asFile
        protected val args = mutableListOf<String>()

        protected abstract val compiler: String

        protected abstract fun resolveArgs()

        fun filePath(file: File): String {
            return file.relativeTo(workingDir).path
        }

        protected fun addCommonArgs() {
            with(args) {
                add("-shared")
                add("-fPIC")
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
        }

        fun run() {
            resolveArgs()

            val execProvider = providers.exec {
                this@exec.workingDir = this@SharedLibraryCompiler.workingDir
                executable = compiler
                args(this@SharedLibraryCompiler.args)

                isIgnoreExitValue = true // So that we can provide better error messages
            }

            try {
                execProvider.result.get().assertNormalExitValue()
            } catch (_: ProcessExecutionException) {
                val formattedArgs = args.joinToString(separator = " ") { "\"$it\"" }
                val stderr = execProvider.standardError.asText.orNull

                throw ProcessExecutionException("Could not start in ${workingDir.path}: $compiler $formattedArgs. Stderr: $stderr")
            }
        }
    }

    private inner class ClangLibraryCompiler(target: JniTarget) : SharedLibraryCompiler(target) {
        override val compiler: String = clangPath.getOrElse("clang")

        override fun resolveArgs() {
            args.add(when (target) {
                JniTarget.MACOS_ARM -> "--target=aarch64-apple-macos"
                JniTarget.MACOS_X64 -> "--target=x86_64-apple-macos"
                JniTarget.WINDOWS_ARM -> "--target=aarch64-w64-mingw32uwp"
                JniTarget.WINDOWS_X64 -> "--target=x86_64-w64-mingw32uwp"
                JniTarget.LINUX_ARM, JniTarget.LINUX_X64 -> throw IllegalStateException()
            })

            addCommonArgs()
        }
    }

    private inner class GccLibraryCompiler(target: JniTarget) : SharedLibraryCompiler(target) {
        override val compiler: String = when (target) {
            JniTarget.LINUX_ARM -> "aarch64-linux-gnu-gcc"
            JniTarget.LINUX_X64 -> "x86_64-linux-gnu-gcc"
            else -> throw IllegalArgumentException("Not a linux target: $target")
        }

        override fun resolveArgs() {
            addCommonArgs()
        }
    }
}
