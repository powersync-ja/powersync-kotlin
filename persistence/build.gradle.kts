import com.powersync.plugins.sonatype.setupGithubRepository
import com.powersync.plugins.utils.powersyncTargets
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    powersyncTargets()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(libs.bundles.sqldelight)
        }

        androidMain.dependencies {
            api(libs.powersync.sqlite.core.android)
            implementation(libs.androidx.sqliteFramework)
        }

        jvmMain.dependencies {
            api(libs.sqldelight.driver.jdbc)
        }

        appleMain.dependencies {
            api(libs.sqldelight.driver.native)
            api(projects.staticSqliteDriver)
        }
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
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    namespace = "com.powersync.persistence"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
}

sqldelight {
    linkSqlite = false

    databases {
        create("PsDatabase") {
            packageName.set("com.powersync.persistence")
            dialect(project(":dialect"))
        }
    }
}

tasks.withType<FormatTask> {
    exclude { it.file.path.contains("generated/") }
}

tasks.withType<LintTask> {
    exclude { it.file.path.contains("generated/") }
}

setupGithubRepository()
