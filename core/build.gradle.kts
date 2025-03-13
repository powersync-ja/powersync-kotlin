import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.powersync.plugins.sonatype.setupGithubRepository
import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.konan.target.Family


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    alias(libs.plugins.mokkery)
}

val sqliteVersion = "3450200"
val sqliteReleaseYear = "2024"

val sqliteSrcFolder =
    project.layout.buildDirectory
        .dir("native/sqlite")
        .get()

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    val destination = sqliteSrcFolder.file(zipFileName).asFile
    src("https://www.sqlite.org/$sqliteReleaseYear/$zipFileName")
    dest(destination)
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(Copy::class) {
    dependsOn(downloadSQLiteSources)

    from(
        zipTree(downloadSQLiteSources.get().dest).matching {
            include("*/sqlite3.*")
            exclude {
                it.isDirectory
            }
            eachFile {
                this.path = this.name
            }
        },
    )
    into(sqliteSrcFolder)
}

val buildCInteropDef by tasks.registering {
    dependsOn(unzipSQLiteSources)

    val interopFolder =
        project.layout.buildDirectory
            .dir("interop/sqlite")
            .get()

    val cFile = sqliteSrcFolder.file("sqlite3.c").asFile
    val defFile = interopFolder.file("sqlite3.def").asFile

    doFirst {
        defFile.writeText(
            """
            package = com.powersync.sqlite3
            ---

            """.trimIndent() + cFile.readText(),
        )
    }
    outputs.files(defFile)
}

val binariesFolder = project.layout.buildDirectory.dir("binaries/desktop")
val downloadPowersyncDesktopBinaries by tasks.registering(Download::class) {
    description = "Download PowerSync core extensions for JVM builds and releases"

    val coreVersion =
        libs.versions.powersync.core
            .get()
    val linux_aarch64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.so"
    val linux_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.so"
    val macos_aarch64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.dylib"
    val macos_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.dylib"
    val windows_x64 =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync_x64.dll"

    val includeAllPlatformsForJvmBuild =
        project.findProperty("powersync.binaries.allPlatforms") == "true"
    val os = OperatingSystem.current()

    // The jar we're releasing for JVM clients needs to include the core extension. For local tests, it's enough to only
    // download the extension for the OS running the build. For releases, we want to include them all.
    // We're not compiling native code for JVM builds here (we're doing that for Android only), so we just have to
    // fetch prebuilt binaries from the powersync-sqlite-core repository.
    if (includeAllPlatformsForJvmBuild) {
        src(listOf(linux_aarch64, linux_x64, macos_aarch64, macos_x64, windows_x64))
    } else {
        val (aarch64, x64) =
            when {
                os.isLinux -> linux_aarch64 to linux_x64
                os.isMacOsX -> macos_aarch64 to macos_x64
                os.isWindows -> null to windows_x64
                else -> error("Unknown operating system: $os")
            }
        val arch = System.getProperty("os.arch")
        src(
            when (arch) {
                "aarch64" -> listOfNotNull(aarch64)
                "amd64", "x86_64" -> listOfNotNull(x64)
                else -> error("Unsupported architecture: $arch")
            },
        )
    }
    dest(binariesFolder.map { it.dir("powersync") })
    onlyIfModified(true)
}

val downloadPowersyncFramework by tasks.registering(Download::class) {
    val coreVersion =
        libs.versions.powersync.core
            .get()
    val framework =
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync-sqlite-core.xcframework.zip"

    src(framework)
    dest(binariesFolder.map { it.file("framework/powersync-sqlite-core.xcframework.zip") })
    onlyIfModified(true)
}

val unzipPowersyncFramework by tasks.registering(Copy::class) {
    dependsOn(downloadPowersyncFramework)

    from(
        zipTree(downloadPowersyncFramework.get().dest).matching {
            include("powersync-sqlite-core.xcframework/**")
        },
    )
    into(binariesFolder.map { it.dir("framework") })
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

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
            cinterops.create("sqlite") {
                val cInteropTask = tasks[interopProcessingTaskName]
                cInteropTask.dependsOn(buildCInteropDef)
                definitionFile =
                    buildCInteropDef
                        .get()
                        .outputs.files.singleFile
                compilerOpts.addAll(listOf("-DHAVE_GETHOSTUUID=0"))
            }
            cinterops.create("powersync-sqlite-core")
        }

        if (konanTarget.family == Family.IOS && konanTarget.name.contains("simulator")) {
            binaries.withType<TestExecutable>().configureEach {
                linkTaskProvider.dependsOn(unzipPowersyncFramework)
                linkerOpts("-framework", "powersync-sqlite-core")
                val frameworkRoot =
                    binariesFolder
                        .map { it.dir("framework/powersync-sqlite-core.xcframework/ios-arm64_x86_64-simulator") }
                        .get()
                        .asFile.path

                linkerOpts("-F", frameworkRoot)
                linkerOpts("-rpath", frameworkRoot)
            }
        }
        /*
        If we ever need macOS support:
        {
            binaries.withType<TestExecutable>().configureEach {
                linkTaskProvider.dependsOn(downloadPowersyncDesktopBinaries)
                linkerOpts("-lpowersync")
                linkerOpts("-L", binariesFolder.map { it.dir("powersync") }.get().asFile.path)
            }
        }
         */
    }

    explicitApi()

    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        val commonIntegrationTest by creating {
            dependsOn(commonTest.get())
        }

        val commonJava by creating {
            kotlin.srcDir("commonJava")
            dependencies {
                implementation(libs.sqlite.jdbc)
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
            implementation(libs.sqlite.jdbc)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqlite.jdbc)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.test.turbine)
            implementation(libs.kermit.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.test.turbine)
        }

        // We're putting the native libraries into our JAR, so integration tests for the JVM can run as part of the unit
        // tests.
        jvmTest.get().dependsOn(commonIntegrationTest)

        androidMain.get().dependsOn(commonJava)
        jvmMain.get().dependsOn(commonJava)

        // We're linking the xcframework for the simulator tests, so they can use integration tests too
        iosSimulatorArm64Test.orNull?.dependsOn(commonIntegrationTest)
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

    namespace = "com.powersync"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        consumerProguardFiles("proguard-rules.pro")

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DSQLITE3_SRC_DIR=${sqliteSrcFolder.asFile.absolutePath}",
                    ),
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = project.file("src/androidMain/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/androidMain/jni", "src/main/jni", "src/jniLibs")
        }
    }
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(downloadPowersyncDesktopBinaries)
}

// We want to build with recent JDKs, but need to make sure we support Java 8. https://jakewharton.com/build-on-latest-java-test-through-lowest-java/
val testWithJava8 by tasks.registering(KotlinJvmTest::class) {
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
        }

    description = "Run tests with Java 8"
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    // Copy inputs from the normal test task
    val testTask = tasks.getByName("jvmTest") as KotlinJvmTest
    classpath = testTask.classpath
    testClassesDirs = testTask.testClassesDirs
}
tasks.named("check").configure { dependsOn(testWithJava8) }

afterEvaluate {
    val buildTasks =
        tasks.matching {
            val taskName = it.name
            if (taskName.contains("Clean")) {
                return@matching false
            }
            if (taskName.contains("externalNative") ||
                taskName.contains("CMake") ||
                taskName.contains(
                    "generateJsonModel",
                )
            ) {
                return@matching true
            }
            return@matching false
        }

    buildTasks.forEach {
        it.dependsOn(buildCInteropDef)
    }
}

setupGithubRepository()
