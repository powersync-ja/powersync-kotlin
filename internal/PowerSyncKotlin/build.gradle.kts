import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.skie)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.version")
}

kotlin {
    val xcf = XCFramework()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
        macosX64(),
        watchosDeviceArm64(),
        watchosArm64(),
        watchosSimulatorArm64(),
        watchosX64(),
    ).forEach {
        it.binaries.framework {
            baseName = "PowerSyncKotlin"
            xcf.add(this)

            export(projects.common)
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
            api(projects.common)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.darwin)
        }

        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("com.powersync.ExperimentalPowerSyncAPI")
            }
        }
    }
}

listOf("Debug", "Release").forEach { buildType ->
    tasks.register<Exec>("build$buildType") {
        group = "build"
        description = "Create an XCFramework archive for $buildType"

        val originalFramework = tasks.getByName("assemblePowerSyncKotlin${buildType}XCFramework")
        dependsOn(originalFramework)

        val source =
            project.layout.buildDirectory
                .map { it.dir("XCFrameworks/${buildType.lowercase()}") }
                .get()
                .asFile
        val archiveFile =
            project.layout.buildDirectory
                .map { it.file("FrameworkArchives/PowersyncKotlin$buildType.zip") }
                .get()
                .asFile

        archiveFile.parentFile.mkdirs()
        archiveFile.delete()

        executable = "zip"
        args("-r", "--symlinks", archiveFile.absolutePath, "PowerSyncKotlin.xcframework")
        workingDir(source)
    }
}
