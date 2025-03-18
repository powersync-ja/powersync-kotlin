import com.powersync.plugins.sonatype.setupGithubRepository
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
            freeCompilerArgs.add("-Xjdk-release=8")
        }
    }

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
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
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
    linkSqlite = false

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
