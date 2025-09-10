package com.powersync.plugins.sharedbuild

import org.gradle.kotlin.dsl.getValue
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

class SharedBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val powersyncFrameworkConfiguration by project.configurations.creating {
            isCanBeConsumed = false
        }

        project.dependencies {
            powersyncFrameworkConfiguration(project(path = ":internal:download-core-extension", configuration = "powersyncFrameworkConfiguration"))
        }

        project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType<KotlinNativeTarget>()
            .configureEach {
                val abiName = when(konanTarget.family) {
                    Family.OSX -> "macos-arm64_x86_64"
                    // We're testing on simulators
                    Family.IOS -> "ios-arm64_x86_64-simulator"
                    Family.WATCHOS -> "watchos-arm64_x86_64-simulator"
                    Family.TVOS -> "tvos-arm64_x86_64-simulator"
                    else -> return@configureEach
                }

                binaries
                    .withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>()
                    .configureEach {
                        val sharedFiles: FileCollection = powersyncFrameworkConfiguration

                        linkTaskProvider.configure {
                            inputs.files(sharedFiles)

                            val frameworkRoot = sharedFiles.singleFile
                                .resolve("powersync-sqlite-core.xcframework/$abiName")
                                .path

                            linkerOpts("-F", frameworkRoot)
                            linkerOpts("-rpath", frameworkRoot)
                        }
                        linkerOpts("-framework", "powersync-sqlite-core")

                    }
            }
    }
}
