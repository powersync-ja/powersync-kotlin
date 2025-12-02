import com.powersync.compile.CreateSqliteCInterop
import com.powersync.plugins.utils.powersyncTargets
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
}

val nativeSqliteConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}
val jniSqlite3McConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    nativeSqliteConfiguration(project(path=":internal:prebuild-binaries", configuration="nativeSqliteConfiguration"))
    jniSqlite3McConfiguration(project(path=":internal:prebuild-binaries", configuration="jniSqlite3McConfiguration"))
}

val hostManager = HostManager()

fun linkSqlite3McCInterop(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val buildCInteropDef = tasks.register("${target.name}CinteropSqlite3Mc", CreateSqliteCInterop::class) {
        val precompiledSqlite: FileCollection = nativeSqliteConfiguration
        inputs.files(precompiledSqlite)

        val staticLibrary = precompiledSqlite.singleFile.resolve("${target.konanTarget.name}sqlite3mc.a")
        archiveFile.set(staticLibrary)
        definitionFile.value(layout.buildDirectory.map { it.file("interopDefs/${target.name}/sqlite3mc.def") })
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
                optIn("com.powersync.PowerSyncInternal")
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
            val interopSource = linkSqlite3McCInterop(this)

            compilations.named("main") {
                cinterops.create("sqlite3mc") {
                    definitionFile.set(interopSource.flatMap { it.definitionFile })
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

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(jniSqlite3McConfiguration)
}
