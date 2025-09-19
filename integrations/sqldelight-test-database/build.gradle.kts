import com.powersync.plugins.utils.powersyncTargets
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.sqldelight)
    id("com.powersync.plugins.sharedbuild")
}

kotlin {
    // We don't test on Android devices, JVM tests are enough for the SQLDelight test package since
    // it doesn't contain Android-specific code.
    powersyncTargets(android = false)

    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
        }
    }
}

sqldelight {
    databases {
        linkSqlite.set(false)

        create("TestDatabase") {
            packageName.set("com.powersync.integrations.sqldelight")
            generateAsync.set(true)
            deriveSchemaFromMigrations.set(false)
            dialect(libs.sqldelight.dialect.sqlite38)
        }
    }
}

tasks.withType<LintTask> {
    exclude { it.file.path.contains("build/generated") }
}

tasks.withType<FormatTask> {
    exclude { it.file.path.contains("build/generated") }
}
