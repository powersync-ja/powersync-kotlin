import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
//    alias(libs.plugins.skie)
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

    cocoapods {
        summary = "A Kotlin Multiplatform library for PowerSync."
        homepage = "none"
        ios.deploymentTarget = "15.2"
    }

//    iosX64() { configureIos() }
    iosArm64 { configureIos() }
    iosSimulatorArm64 { configureIos() }

//    targets.withType<KotlinNativeTarget>().all {
//        compilations.getByName("main") {
//            cinterops.create("sqlite") {
//                defFile("src/nativeInterop/cinterop/sqlite3.def")
//            }
//
//            cinterops.create("PowerSyncSqlitePlugin") {
//                includeDirs("$projectDir/src/include")
//            }
//        }
//        binaries.withType<Framework> {
//            linkerOpts.add("-lsqlite3")
//        }
//    }

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

fun KotlinNativeTarget.configureIos() {
    val frameworkPath = file("${rootDir}/frameworks")
        .resolveArchPath(konanTarget, "powersync-sqlite-core")
    println("frameworkPath: $frameworkPath")
    compilations.getByName("main") {
        cinterops.create("powersync-sqlite-core") {
            compilerOpts("-framework", "powersync-sqlite-core", "-F$frameworkPath")
        }
    }

    binaries {

        getTest("DEBUG").apply {
            linkerOpts(
                "-framework",
                "powersync-sqlite-core",
                "-F$frameworkPath",
                "-rpath",
                "$frameworkPath",
                "-ObjC"
            )
        }
    }
}

fun File.resolveArchPath(target: KonanTarget, framework: String): File? {
    val archPaths = resolve("$framework.xcframework")
        .listFiles { _, name -> target.matches(name) }
        ?: return null

    check(archPaths.size == 1) { "Resolving framework '$framework' arch path failed: $archPaths" }

    return archPaths.first()
}

fun KonanTarget.matches(dir: String): Boolean {
    return when (this) {
        KonanTarget.IOS_SIMULATOR_ARM64,
        KonanTarget.IOS_X64 -> dir.startsWith("ios") && dir.endsWith("simulator")

        KonanTarget.IOS_ARM64 -> dir.startsWith("ios-arm64") && !dir.contains("x86")

        else -> error("Unsupported target $name")
    }
}

