import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask
import com.powersync.compile.CreateSqliteCInterop
import com.powersync.plugins.utils.powersyncTargets
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    id("dokka-convention")
}

val nativeSqliteConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}
val jniSqlite3McConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}
val androidBuildSourceConfiguration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    nativeSqliteConfiguration(project(path=":internal:prebuild-binaries", configuration="nativeSqliteConfiguration"))
    jniSqlite3McConfiguration(project(path=":internal:prebuild-binaries", configuration="jniSqlite3McConfiguration"))
    androidBuildSourceConfiguration(project(path=":internal:prebuild-binaries", configuration="androidBuildSourceConfiguration"))
}

val hostManager = HostManager()

fun linkSqlite3McCInterop(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val buildCInteropDef = tasks.register("${target.name}CinteropSqlite3Mc", CreateSqliteCInterop::class) {
        val precompiledSqlite: FileCollection = nativeSqliteConfiguration
        inputs.files(precompiledSqlite)
        dependsOn(precompiledSqlite)

        val staticLibrary = precompiledSqlite.singleFile.resolve("${target.konanTarget.name}sqlite3mc.a")
        archiveFile.set(staticLibrary)
        definitionFile.value(layout.buildDirectory.map { it.file("interopDefs/${target.name}/sqlite3mc.def") })
    }

    return buildCInteropDef
}

kotlin {
    powersyncTargets(android = {
        namespace = "com.powersync.encryption"
    })

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

            dependencies {
                implementation(projects.internal.sqlite3mcandroid)
            }
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

val generateCmake by tasks.registering(Copy::class) {
    from(androidBuildSourceConfiguration)
    into(layout.buildDirectory.dir("androidJniBuild"))
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    from(jniSqlite3McConfiguration)
}

dokka {
    moduleName.set("Encryption (SQLite3MultipleCiphers)")
}
