import co.touchlab.faktory.versionmanager.TimestampVersionManager

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

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}

addGithubPackagesRepository()

kmmbridge {
    mavenPublishArtifacts()
    spm()
    versionManager.set(TimestampVersionManager)
}
