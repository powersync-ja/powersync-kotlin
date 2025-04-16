package com.powersync.plugins.sharedbuild

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

class SharedBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val binariesFolder = project.layout.buildDirectory.dir("binaries")

        val coreVersion =
            project.extensions
                .getByType(VersionCatalogsExtension::class.java)
                .named("libs")
                .findVersion("powersync.core")
                .get()
                .toString()

        val frameworkUrl =
            "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync-sqlite-core.xcframework.zip"

        val downloadPowersyncFramework =
            project.tasks.register("downloadPowersyncFramework", Download::class.java) {
                src(frameworkUrl)
                dest(binariesFolder.map { it.file("framework/powersync-sqlite-core.xcframework.zip") })
                onlyIfModified(true)
            }

        val unzipPowersyncFramework =
            project.tasks.register("unzipPowersyncFramework", Exec::class.java) {
                dependsOn(downloadPowersyncFramework)

                val zipfile = downloadPowersyncFramework.get().dest
                inputs.file(zipfile)
                val destination = File(zipfile.parentFile, "extracted")
                doFirst {
                    destination.deleteRecursively()
                    destination.mkdir()
                }

                // We're using unzip here because the Gradle copy task doesn't support symlinks.
                executable = "unzip"
                args(zipfile.absolutePath)
                workingDir(destination)
                outputs.dir(destination)
            }

        project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType<KotlinNativeTarget>()
            .configureEach {
                if (konanTarget.family == Family.IOS &&
                    konanTarget.name.contains(
                        "simulator",
                    )
                ) {
                    binaries
                        .withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>()
                        .configureEach {
                            linkTaskProvider.configure { dependsOn(unzipPowersyncFramework) }
                            linkerOpts("-framework", "powersync-sqlite-core")

                            val frameworkRoot =
                                binariesFolder
                                    .map { it.dir("framework/powersync-sqlite-core.xcframework/ios-arm64_x86_64-simulator") }
                                    .get()
                                    .asFile.path

                            linkerOpts("-F", frameworkRoot)
                            linkerOpts("-rpath", frameworkRoot)
                        }
                } else if (konanTarget.family == Family.OSX) {
                    binaries
                        .withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>()
                        .configureEach {
                            linkTaskProvider.configure { dependsOn("unzipPowersyncFramework") }
                            linkerOpts("-framework", "powersync-sqlite-core")
                            var frameworkRoot =
                                binariesFolder
                                    .map { it.dir("framework/extracted/powersync-sqlite-core.xcframework/macos-arm64_x86_64") }
                                    .get()
                                    .asFile.path

                            linkerOpts("-F", frameworkRoot)
                            linkerOpts("-rpath", frameworkRoot)
                        }
                }
            }
    }
}
