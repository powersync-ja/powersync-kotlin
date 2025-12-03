import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    id("dokka-convention")
}

kotlin {
    powersyncTargets(
        includeTargetsWithoutComposeSupport = false,
        // Recent versions of Compose Multiplatform generate bytecode with Java 11, which we have
        // to adopt as well
        legacyJavaSupport = false,
    )

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(projects.common)
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

dokka {
    moduleName.set("PowerSync Compose")
}
