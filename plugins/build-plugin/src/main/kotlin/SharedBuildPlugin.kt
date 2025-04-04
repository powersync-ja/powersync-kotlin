package com.powersync.plugins.sharedbuild

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

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

        project.tasks.register("unzipPowersyncFramework", Copy::class.java) {
            dependsOn(downloadPowersyncFramework)

            from(
                project.zipTree(downloadPowersyncFramework.get().dest).matching {
                    include("powersync-sqlite-core.xcframework/**")
                },
            )
            into(binariesFolder.map { it.dir("framework") })
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
                            linkTaskProvider.configure { dependsOn("unzipPowersyncFramework") }
                            linkerOpts("-framework", "powersync-sqlite-core")

                            val frameworkRoot =
                                binariesFolder
                                    .map { it.dir("framework/powersync-sqlite-core.xcframework/ios-arm64_x86_64-simulator") }
                                    .get()
                                    .asFile.path

                            linkerOpts("-F", frameworkRoot)
                            linkerOpts("-rpath", frameworkRoot)
                        }
                }
            }
    }
}
