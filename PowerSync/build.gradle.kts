import co.touchlab.faktory.versionmanager.TimestampVersionManager
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

kmmbridge {
    mavenPublishArtifacts()
    spm()
    versionManager.set(TimestampVersionManager)
}

if (System.getenv().containsKey("CI")) {
    // Setup github publishing based on GitHub action variables
    addGithubPackagesRepository()
}