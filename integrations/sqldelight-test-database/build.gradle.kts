import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm {
        // We don't need other targets, this is only used for tests. Since nothing in the SQLDelight
        // driver is platform-specific, we only test with JVM.
    }

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
