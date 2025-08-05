import com.powersync.plugins.sonatype.setupGithubRepository
import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("dokka-convention")
}

kotlin {
    powersyncTargets(includeTargetsWithoutComposeSupport = false)

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(project(":persistence"))
            implementation(compose.runtime)
        }
        androidMain.dependencies {
            implementation(compose.foundation)
        }
    }
}

android {
    namespace = "com.powersync.compose"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    kotlin {
        jvmToolchain(17)
    }
}

setupGithubRepository()

dokka {
    moduleName.set("PowerSync Compose")
}
