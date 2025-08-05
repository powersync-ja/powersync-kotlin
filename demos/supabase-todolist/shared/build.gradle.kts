import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.cocoapods)
    alias(libs.plugins.buildKonfig)
}

version = "1.0-SNAPSHOT"

kotlin {
    androidTarget()

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = "1.0.0"
        summary = "A shared library for TodoAppLite PowerSync app"
        homepage = "none"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        pod("powersync-sqlite-core") {
            version = "0.4.0"
            linkOnly = true
        }

        framework {
            baseName = "shared"
            isStatic = true
            export("com.powersync:core")
        }
    }
    sourceSets {
        commonMain.dependencies {
            // When copying this example, use the current version available
            // at: https://central.sonatype.com/artifact/com.powersync/core
            api(projects.core) // "com.powersync:core"
            implementation(projects.connectors.supabase) // "com.powersync:connector-supabase"
            implementation(projects.compose) // "com.powersync:compose"
            implementation(libs.uuid)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.kmp.lifecycle.compose)
            implementation(libs.supabase.client)
            api(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            api(libs.androidx.appcompat)
            api(libs.androidx.core)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.powersync.demos"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(
            libs.versions.java
                .get()
                .toInt(),
        )
    }
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = parent!!.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

buildkonfig {
    packageName = "com.powersync.demos"
    objectName = "Config"

    defaultConfigs {
        fun stringConfigField(name: String) {
            val propValue = localProperties.getProperty(name, "")
            if (propValue.isBlank()) {
                println("Warning: Property $name not found in local.properties")
            }

            buildConfigField(STRING, name, propValue)
        }

        stringConfigField("POWERSYNC_URL")
        stringConfigField("SUPABASE_URL")
        stringConfigField("SUPABASE_ANON_KEY")
    }
}
