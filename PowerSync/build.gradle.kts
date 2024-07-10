import co.touchlab.faktory.versionmanager.TimestampVersionManager
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kmmbridge)
    alias(libs.plugins.skie)
    alias(libs.plugins.mavenPublishPlugin)
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            export(project(":core"))
            isStatic = true
        }
    }

    explicitApi()

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}

skie {
    features {
        group {
            // We turn this off as the suspend interop feature results in
            // threading issues when implementing SDK in Swift
            SuspendInterop.Enabled(false)
        }
    }
}

kmmbridge {
    mavenPublishArtifacts()
    spm()
    versionManager.set(TimestampVersionManager)
}

if (System.getenv().containsKey("CI")) {
    // Setup github publishing based on GitHub action variables
    addGithubPackagesRepository()
}