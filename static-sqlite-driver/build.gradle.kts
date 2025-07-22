import com.powersync.compile.ClangCompile
import com.powersync.compile.CreateSqliteCInterop
import com.powersync.compile.CreateStaticLibrary
import com.powersync.compile.UnzipSqlite
import java.io.File
import com.powersync.plugins.sonatype.setupGithubRepository
import com.powersync.plugins.utils.powersyncTargets
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

val sqliteVersion = "3500300"
val sqliteReleaseYear = "2025"

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    src("https://www.sqlite.org/$sqliteReleaseYear/$zipFileName")
    dest(layout.buildDirectory.dir("downloads").map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(UnzipSqlite::class) {
    val zip = downloadSQLiteSources.map { it.outputs.files.singleFile }
    inputs.file(zip)

    unzipSqlite(
        src = zipTree(zip),
        dir = layout.buildDirectory.dir("downloads/sqlite3")
    )
}

// Obtain host and platform manager from Kotlin multiplatform plugin. They're supposed to be
// internal, but it's very convenient to have them because they expose the necessary toolchains we
// use to compile SQLite for the platforms we need.
val hostManager = HostManager()

fun compileSqlite(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val name = target.targetName
    val outputDir = layout.buildDirectory.dir("c/$name")

    val sqlite3Obj = outputDir.map { it.file("sqlite3.o") }
    val archive = outputDir.map { it.file("libsqlite3.a") }

    val compileSqlite = tasks.register("${name}CompileSqlite", ClangCompile::class) {
        inputs.dir(unzipSQLiteSources.map { it.destination })

        inputFile.set(unzipSQLiteSources.flatMap { it.destination.file("sqlite3.c") })
        konanTarget.set(target.konanTarget.name)
        include.set(unzipSQLiteSources.flatMap { it.destination })
        objectFile.set(sqlite3Obj)
    }

    val createStaticLibrary = tasks.register("${name}ArchiveSqlite", CreateStaticLibrary::class) {
        inputs.file(compileSqlite.map { it.objectFile })
        objects.from(sqlite3Obj)
        staticLibrary.set(archive)
    }

    val buildCInteropDef = tasks.register("${name}CinteropSqlite", CreateSqliteCInterop::class) {
        inputs.file(createStaticLibrary.map { it.staticLibrary })

        archiveFile.set(archive)
        definitionFile.fileProvider(archive.map { File(it.asFile.parentFile, "sqlite3.def") })
    }

    return buildCInteropDef
}

kotlin {
    // We use sqlite3-jdbc on JVM platforms instead
    powersyncTargets(jvm=false)

    applyDefaultHierarchyTemplate()
    explicitApi()

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.cinterop.BetaInteropApi")
            }
        }

        nativeTest {
            dependencies {
                implementation(projects.drivers.common)
            }
        }
    }

    targets.withType<KotlinNativeTarget> {
        if (hostManager.isEnabled(konanTarget)) {
            val compileSqlite3 = compileSqlite(this)

            compilations.named("main") {
                cinterops.create("sqlite3") {
                    definitionFile.set(compileSqlite3.flatMap { it.definitionFile })
                }
            }
        }
    }
}

setupGithubRepository()
