import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    //    iosX64() Disabled for now, uncomment when we you are not on an M1 Mac
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            cinterops.create("sqlite") {
                defFile = project.file("src/nativeInterop/cinterop/sqlite3.def")
            }
            cinterops.create("powersync-sqlite-core")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.io)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.sqldelight.common)
        }
        androidMain.dependencies {
            implementation(libs.powersync.sqlite.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.requery.sqlite.android)
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
            implementation(libs.sqldelight.driver.ios)
        }
    }
}

android {
    namespace = "co.powersync"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("PsDatabase") {
            packageName.set("co.powersync.db")
        }
    }
    linkSqlite = true
}
