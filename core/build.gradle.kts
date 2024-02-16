import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    id("com.powersync.plugins.sonatype")
}

// List of flags we use to compile SQLite.
// See: https://www.sqlite.org/compile.html
// TODO(b/310681164): Validate these flags and compare to other platforms.
val SQLITE_COMPILE_FLAGS = listOf(
    "-DHAVE_USLEEP=1",
    "-DSQLITE_DEFAULT_MEMSTATUS=0",
    "-DSQLITE_ENABLE_COLUMN_METADATA=1",
    "-DSQLITE_ENABLE_FTS3=1",
    "-DSQLITE_ENABLE_FTS3_PARENTHESIS=1",
    "-DSQLITE_ENABLE_FTS4=1",
    "-DSQLITE_ENABLE_FTS5=1",
    "-DSQLITE_ENABLE_JSON1=1",
    "-DSQLITE_ENABLE_LOAD_EXTENSION=1",
    "-DSQLITE_ENABLE_NORMALIZE=1",
    "-DSQLITE_ENABLE_RBU=1",
    "-DSQLITE_ENABLE_RTREE=1",
    "-DSQLITE_ENABLE_STAT4=1",
    "-DSQLITE_OMIT_PROGRESS_CALLBACK=0",
    "-DSQLITE_THREADSAFE=2",
)

val sqliteVersion = "3380500"
val sqliteReleaseYear = "2022"

val sqliteSrcFolder =
    project.layout.buildDirectory.dir("interop/sqlite").get()

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    src("https://www.sqlite.org/$sqliteReleaseYear/${zipFileName}")
    val destination = sqliteSrcFolder.file(zipFileName).asFile
    dest(destination)
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(Copy::class) {
    dependsOn(downloadSQLiteSources)

    from(zipTree(downloadSQLiteSources.get().dest).matching {
        include("*/sqlite3.*")
        exclude {
            it.isDirectory
        }
        eachFile {
            this.path = this.name
        }
    })
    into(sqliteSrcFolder)
}

val buildCInteropDef by tasks.registering {

    dependsOn(unzipSQLiteSources)

    val cFile = sqliteSrcFolder.file("sqlite3.c").asFile
    val defFile = sqliteSrcFolder.file("sqlite3.def").asFile

    doFirst {
        defFile.writeText(
            """
            package = com.powersync.sqlite3
            ---
            
        """.trimIndent() + cFile.readText()
        )
    }
    outputs.files(defFile)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    //    iosX64() Disabled for now, uncomment when we you are not on an M1 Mac
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            cinterops.create("sqlite") {
                val cInteropTask = tasks[interopProcessingTaskName]
                cInteropTask.dependsOn(buildCInteropDef)

                defFile = buildCInteropDef.get().outputs.files.singleFile
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
            implementation(libs.uuid)
            implementation(libs.kotlin.stdlib)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentnegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.io)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.stately.concurrency)
            implementation(libs.bundles.sqldelight)
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

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


android {
    kotlin {
        jvmToolchain(17)
    }

    namespace = "com.powersync"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf("-DSQLITE3_SRC_DIR=${sqliteSrcFolder.asFile.absolutePath}")
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = project.file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
}

sqldelight {
    databases {
        create("PsDatabase") {
            packageName.set("com.powersync.db")
            generateAsync = true
            dialect(project(":dialect"))
        }
    }
    linkSqlite = true
}

sonatypePublishing {
    setupGithubRepository()
}
