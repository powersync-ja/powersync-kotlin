import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.sqldelight)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    // Disabling Android for simplicity, we're only testing the common driver anyway
    powersyncTargets(android=false)
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
