import com.powersync.compile.CreateSqliteCInterop
import com.powersync.plugins.utils.powersyncTargets
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

val nativeSqliteConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    nativeSqliteConfiguration(project(path=":internal:prebuild-binaries", configuration="nativeSqliteConfiguration"))
}

fun linkSqliteCInterop(target: KotlinNativeTarget): TaskProvider<CreateSqliteCInterop> {
    val buildCInteropDef = tasks.register("${target.name}CinteropSqlite", CreateSqliteCInterop::class) {
        val precompiledSqlite: FileCollection = nativeSqliteConfiguration
        inputs.files(precompiledSqlite)
        dependsOn(precompiledSqlite)

        val staticLibrary = precompiledSqlite.singleFile.resolve("${target.konanTarget.name}sqlite3.a")
        archiveFile.set(staticLibrary)
        definitionFile.value(layout.buildDirectory.map { it.file("interopDefs/${target.name}/sqlite3.def") })
    }

    return buildCInteropDef
}

// Obtain host and platform manager from Kotlin multiplatform plugin. They're supposed to be
// internal, but it's very convenient to have them because they expose the necessary toolchains we
// use to compile SQLite for the platforms we need.
val hostManager = HostManager()

kotlin {
    // We use sqlite3-bundled on JVM platforms instead
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
    }

    targets.withType<KotlinNativeTarget> {
        if (hostManager.isEnabled(konanTarget)) {
            val interopSource = linkSqliteCInterop(this)

            compilations.named("main") {
                cinterops.create("sqlite3") {
                    definitionFile.set(interopSource.flatMap { it.definitionFile })
                }
            }
        }
    }
}
