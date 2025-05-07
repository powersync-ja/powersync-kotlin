import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.skie)
    alias(libs.plugins.kotlinter)
}

kotlin {
    val xcf = XCFramework()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
        macosX64(),
    ).forEach {
        it.binaries.framework {
            baseName = "PowerSyncKotlin"
            xcf.add(this)

            export(project(":core"))
            isStatic = true

            binaryOption("bundleId", "PowerSyncKotlin")
            binaryOption("bundleVersion", project.version.toString())
        }
    }

    explicitApi()

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}

repositories {
    maven {
        name = "PowerSyncSQLiterFork"
        url = uri("https://powersync-ja.github.io/SQLiter")
        content {
            includeModuleByRegex("co.touchlab", "sqliter-driver.*")
        }
    }
}

configurations.all {
    resolutionStrategy {
        // This version has not been released yet (https://github.com/touchlab/SQLiter/pull/124), so we're pointing this
        // towards our fork with the repositories block above.
        // The API is identical, but we have to make sure this particular project builds the xcframework with the
        // patched SQLiter version to avoid linker errors on macOS.
        force("co.touchlab:sqliter-driver:1.3.2-powersync")
    }
}

listOf("Debug", "Release").forEach { buildType ->
    tasks.register<Exec>("build$buildType") {
        group = "build"
        description = "Create an XCFramework archive for $buildType"

        val originalFramework = tasks.getByName("assemblePowerSyncKotlin${buildType}XCFramework")
        dependsOn(originalFramework)

        val source = project.layout.buildDirectory.map { it.dir("XCFrameworks/${buildType.lowercase()}") }.get().asFile
        val archiveFile = project.layout.buildDirectory.map { it.file("FrameworkArchives/PowersyncKotlin$buildType.zip") }.get().asFile

        archiveFile.parentFile.mkdirs()
        archiveFile.delete()

        executable = "zip"
        args("-r", "--symlinks", archiveFile.absolutePath, "PowerSyncKotlin.xcframework")
        workingDir(source)
    }
}
