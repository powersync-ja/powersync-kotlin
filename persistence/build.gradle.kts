import com.powersync.plugins.sonatype.setupGithubRepository

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    explicitApi()

    sourceSets {

        commonMain.dependencies {
            api(libs.bundles.sqldelight)
        }

        androidMain.dependencies {
            api(libs.sqldelight.driver.android)
            api(libs.powersync.sqlite.core.android)
            api(libs.requery.sqlite.android)
            implementation(libs.androidx.sqliteFramework)
        }

        jvmMain.dependencies {
            api(libs.sqldelight.driver.jdbc)
        }

        iosMain.dependencies {
            api(libs.sqldelight.driver.ios)
        }
    }
}

android {
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
        }
    }
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    namespace = "com.powersync.persistence"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
}

sqldelight {
    databases {
        create("PsDatabase") {
            packageName.set("com.powersync.persistence")
            dialect(project(":dialect"))
        }
    }
}

tasks.formatKotlinCommonMain {
    exclude { it.file.path.contains("generated/") }
}

tasks.lintKotlinCommonMain {
    exclude { it.file.path.contains("generated/") }
}

setupGithubRepository()
