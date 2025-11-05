import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.powersync.compile.ClangCompile
import com.powersync.compile.CreateSqliteCInterop
import com.powersync.compile.UnzipSqlite
import com.powersync.plugins.utils.powersyncTargets
import de.undercouch.gradle.tasks.download.Download
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
}

val sqlite3McVersion = "2.2.4"
val sqlite3BaseVersion = "3.50.4"

val downloadSqlite3Mc by tasks.registering(Download::class) {
    val zipFileName = "sqlite3mc-$sqlite3McVersion.zip"
    src("https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$sqlite3McVersion/sqlite3mc-$sqlite3McVersion-sqlite-$sqlite3BaseVersion-amalgamation.zip")
    dest(layout.buildDirectory.dir("downloads").map { it.file(zipFileName) })
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(UnzipSqlite::class) {
    val zip = downloadSqlite3Mc.map { it.outputs.files.singleFile }
    inputs.file(zip)

    unzipSqlite(
        src = zipTree(zip),
        dir = layout.buildDirectory.dir("downloads/sqlite3mc"),
        filter = null,
    )
}

val hostManager = HostManager()

fun compileSqlite3McForKotlinNative(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val name = target.targetName
    val outputDir = layout.buildDirectory.dir("c/$name")

    val sqlite3Obj = outputDir.map { it.file("sqlite3mc.o") }
    val archive = outputDir.map { it.file("libsqlite3mc.a") }

    val compileSqlite = tasks.register("${name}CompileSqlite", ClangCompile::class) {
        dependsOn(unzipSQLiteSources)
        inputs.dir(unzipSQLiteSources.map { it.destinationDir })

        inputFile.set(unzipSQLiteSources.flatMap { it.destination.file("sqlite3mc_amalgamation.c") })
        konanTarget.set(target.konanTarget.name)
        include.set(unzipSQLiteSources.flatMap { it.destination })
        objectFile.set(sqlite3Obj)
    }

    val createStaticLibrary = tasks.register("${name}ArchiveSqlite", com.powersync.compile.CreateStaticLibrary::class) {
        inputs.file(compileSqlite.map { it.objectFile })
        objects.from(sqlite3Obj)
        staticLibrary.set(archive)
    }

    val buildCInteropDef = tasks.register("${name}CinteropSqlite", CreateSqliteCInterop::class) {
        inputs.file(createStaticLibrary.map { it.staticLibrary })

        archiveFile.set(archive)
        definitionFile.fileProvider(archive.map { File(it.asFile.parentFile, "sqlite3mc.def") })
    }

    return buildCInteropDef
}

kotlin {
    powersyncTargets()

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

        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }

        androidMain {
            dependsOn(jvmAndroidMain)
        }

        jvmMain {
            dependsOn(jvmAndroidMain)
        }

        commonMain.dependencies {
            api(projects.common)
            implementation(libs.androidx.sqlite.sqlite)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            api(libs.test.kotest.assertions)
        }
    }

    targets.withType<KotlinNativeTarget> {
        if (hostManager.isEnabled(konanTarget)) {
            val compileSqlite3 = compileSqlite3McForKotlinNative(this)

            compilations.named("main") {
                cinterops.create("sqlite3mc") {
                    definitionFile.set(compileSqlite3.flatMap { it.definitionFile })
                }
            }
        }
    }
}

android {
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "com.powersync.encryption"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
//        consumerProguardFiles("proguard-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path("src/jni/CMakeLists.txt")
        }
    }

    ndkVersion = "27.1.12297006"
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(unzipSQLiteSources)
}

val xCodeInstallation = ClangCompile.resolveXcode(providers)

// Tasks to build the JNI shared library for multiple operating systems.
// Since the JNI sources rarely change, we don't run these tasks on every build. Instead,
// we'll publish these sources as one-off releases when needed, and then reference that URL.
fun registerCompileMacOsHostTask(architecture: String): TaskProvider<Exec> {
    return tasks.register<Exec>("jniCompileMacos$architecture") {
        val xcode = Path(xCodeInstallation.get())
        val toolchain =
            xcode.resolve("Toolchains/XcodeDefault.xctoolchain/usr/bin").absolutePathString()

        val outputDirectory = layout.buildDirectory.dir("jni-build/macos")
        val outputFile = outputDirectory.map { it.file("libsqlite3mc_jni.$architecture.dylib") }
        outputs.file(outputFile)

        dependsOn(unzipSQLiteSources)
        val sqlite3McSources = unzipSQLiteSources.map { it.destinationDir }
        inputs.dir(sqlite3McSources)

        val headers = layout.projectDirectory.dir("src/jni/headers/inc_mac")
        inputs.dir(headers)

        doFirst {
            outputDirectory.get().asFile.mkdirs()
        }

        executable = "clang"
        args(
            "-B$toolchain",
            "-dynamiclib",
            "-fPIC",
            "--target=${architecture}-apple-macos",
            "-o",
            outputFile.get().asFile.path,
            "src/jni/sqlite_bindings.cpp",
            File(sqlite3McSources.get(), "sqlite3mc_amalgamation.c").path,
            "-I",
            sqlite3McSources.get().path,
            "-I",
            headers.asFile.path,
            "-O3",
            *ClangCompile.sqlite3ClangOptions,
        )
    }
}

val macosArm64 = registerCompileMacOsHostTask("aarch64")
val macosX64 = registerCompileMacOsHostTask("x86_64")

tasks.register("jniCompile") {
    dependsOn(macosArm64)
    dependsOn(macosX64)
}
