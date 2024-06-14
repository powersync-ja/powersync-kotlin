import com.powersync.plugins.sonatype.setupGithubRepository
import de.undercouch.gradle.tasks.download.Download
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    id("com.powersync.plugins.sonatype")
}

val sqliteVersion = "3450000"
val sqliteReleaseYear = "2024"

val osName = when {
    Os.isFamily(Os.FAMILY_WINDOWS) -> "Windows"
    Os.isFamily(Os.FAMILY_MAC) -> "macOS"
    Os.isFamily(Os.FAMILY_UNIX) -> "Linux"
    else -> "Unknown"
}

val sqliteSrcFolder = project.layout.buildDirectory.dir("interop/sqlite").get()
val jvmNativeBuildFolder = "${layout.buildDirectory.get().asFile}/native/powersync-sqlite"

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    val destination = sqliteSrcFolder.file(zipFileName).asFile
    src("https://www.sqlite.org/$sqliteReleaseYear/${zipFileName}")
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

tasks.create<Exec>("buildNative") {
    group = "build"

    outputs.dir(jvmNativeBuildFolder)
    workingDir = file(jvmNativeBuildFolder)

    environment("TARGET", "$osName/cmake")
    environment("SOURCE_PATH", "$projectDir/src/jvmMain/cpp")
    environment("INTEROP_PATH", "$sqliteSrcFolder")

    commandLine("$projectDir/src/jvmMain/cpp/build.sh")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm {
        val processResources = compilations["main"].processResourcesTaskName
        (tasks[processResources] as ProcessResources).apply {
            dependsOn("buildNative")
            from("$jvmNativeBuildFolder/${osName}/output")
        }
    }

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
            cinterops.create("sqlite") {
                val cInteropTask = tasks[interopProcessingTaskName]
                cInteropTask.dependsOn(buildCInteropDef)
                defFile = buildCInteropDef.get().outputs.files.singleFile
                compilerOpts.addAll(listOf("-DHAVE_GETHOSTUUID=0"))
            }
            cinterops.create("powersync-sqlite-core")
        }
    }

    explicitApi()

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
            implementation(libs.configuration.annotations)
            api(project(":persistence"))
            api(libs.kermit)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
        }

        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.desktop)
            implementation(libs.ktor.client.okhttp)
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

    namespace = "com.powersync"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DSQLITE3_SRC_DIR=${sqliteSrcFolder.asFile.absolutePath}"
                    )
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


afterEvaluate {
    val buildTasks = tasks.matching {
        val taskName = it.name
        if (taskName.contains("Clean")) {
            return@matching false
        }
        if (taskName.contains("externalNative") || taskName.contains("CMake") || taskName.contains("generateJsonModel")) {
            return@matching true
        }
        return@matching false
    }

    buildTasks.forEach {
        it.dependsOn(buildCInteropDef)
    }
}

setupGithubRepository()
