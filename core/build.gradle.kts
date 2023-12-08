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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.serialization.json)
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

//        targets.withType<KotlinNativeTarget>().all {
//            val mainCompilation = compilations.getByName("main")
//
//            mainCompilation.cinterops.create("PowerSyncSqlite") {
//                includeDirs("$projectDir/src/include")
//            }
//        }
    }
}

android {
    namespace = "co.powersync.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("co.powersync.core")
        }
    }
    linkSqlite = true
}
