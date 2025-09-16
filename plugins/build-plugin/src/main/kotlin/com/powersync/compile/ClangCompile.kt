package com.powersync.compile

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.KonanTarget
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@CacheableTask
abstract class ClangCompile : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val konanTarget: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val include: DirectoryProperty

    @get:OutputFile
    abstract val objectFile: RegularFileProperty

    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:Input
    val xcodeInstallation: Provider<String>
        get() =
            providers
                .exec {
                    executable("xcode-select")
                    args("-p")
                }.standardOutput.asText

    @TaskAction
    fun run() {
        val target = requireNotNull(KonanTarget.predefinedTargets[konanTarget.get()])
        val xcodePath = xcodeInstallation.get().trim()
        if (xcodePath.isEmpty()) {
            throw GradleException("xcode-select was unable to resolve an XCode installation")
        }

        val xcode = Path(xcodePath)
        val toolchain =
            xcode.resolve("Toolchains/XcodeDefault.xctoolchain/usr/bin").absolutePathString()

        val (llvmTarget, sysRoot) =
            when (target) {
                KonanTarget.IOS_X64 -> "x86_64-apple-ios12.0-simulator" to IOS_SIMULATOR_SDK
                KonanTarget.IOS_ARM64 -> "arm64-apple-ios12.0" to IOS_SDK
                KonanTarget.IOS_SIMULATOR_ARM64 -> "arm64-apple-ios14.0-simulator" to IOS_SIMULATOR_SDK
                KonanTarget.MACOS_ARM64 -> "aarch64-apple-macos" to MACOS_SDK
                KonanTarget.MACOS_X64 -> "x86_64-apple-macos" to MACOS_SDK
                KonanTarget.WATCHOS_DEVICE_ARM64 -> "aarch64-apple-watchos" to WATCHOS_SDK
                KonanTarget.WATCHOS_ARM32 -> "armv7k-apple-watchos" to WATCHOS_SDK
                KonanTarget.WATCHOS_ARM64 -> "arm64_32-apple-watchos" to WATCHOS_SDK
                KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "aarch64-apple-watchos-simulator" to WATCHOS_SIMULATOR_SDK
                KonanTarget.WATCHOS_X64 -> "x86_64-apple-watchos-simulator" to WATCHOS_SIMULATOR_SDK
                KonanTarget.TVOS_ARM64 -> "aarch64-apple-tvos" to TVOS_SDK
                KonanTarget.TVOS_X64 -> "x86_64-apple-tvos-simulator" to TVOS_SIMULATOR_SDK
                KonanTarget.TVOS_SIMULATOR_ARM64 -> "aarch64-apple-tvos-simulator" to TVOS_SIMULATOR_SDK
                else -> error("Unexpected target $target")
            }

        val output = objectFile.get()

        providers
            .exec {
                executable = "clang"
                args(
                    "-B$toolchain",
                    "-fno-stack-protector",
                    "-target",
                    llvmTarget,
                    "-isysroot",
                    xcode.resolve(sysRoot).absolutePathString(),
                    "-fPIC",
                    "--compile",
                    "-I${include.get().asFile.absolutePath}",
                    inputFile.get().asFile.absolutePath,
                    "-DHAVE_GETHOSTUUID=0",
                    "-DSQLITE_ENABLE_DBSTAT_VTAB",
                    "-DSQLITE_ENABLE_FTS5",
                    "-DSQLITE_ENABLE_RTREE",
                    "-DSQLITE_ENABLE_SNAPSHOT",
                    "-O3",
                    "-o",
                    output.asFile.toPath().name,
                )

                workingDir = output.asFile.parentFile
            }.result
            .get()
    }

    companion object {
        const val WATCHOS_SDK = "Platforms/WatchOS.platform/Developer/SDKs/WatchOS.sdk"
        const val WATCHOS_SIMULATOR_SDK =
            "Platforms/WatchSimulator.platform/Developer/SDKs/WatchSimulator.sdk/"
        const val IOS_SDK = "Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
        const val IOS_SIMULATOR_SDK =
            "Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
        const val TVOS_SDK = "Platforms/AppleTVOS.platform/Developer/SDKs/AppleTVOS.sdk"
        const val TVOS_SIMULATOR_SDK =
            "Platforms/AppleTVSimulator.platform/Developer/SDKs/AppleTVSimulator.sdk"
        const val MACOS_SDK = "Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/"
    }
}
