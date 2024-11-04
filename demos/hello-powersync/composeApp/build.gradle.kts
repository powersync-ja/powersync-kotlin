import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.ExperimentalComposeLibrary
import java.util.Properties

plugins {
    alias(projectLibs.plugins.kotlinMultiplatform)
    alias(projectLibs.plugins.cocoapods)
    alias(projectLibs.plugins.androidApplication)
    alias(projectLibs.plugins.jetbrainsCompose)
    alias(projectLibs.plugins.compose.compiler)
    alias(projectLibs.plugins.skie)
    alias(libs.plugins.buildKonfig)
}

kotlin {
    androidTarget()

//    iosX64() // uncomment to enable iOS x64
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        version = "1.0.0"
        summary = "A shared library for Hello PowerSync app"
        homepage = "none"
        ios.deploymentTarget = "15.2"
        podfile = project.file("../iosApp/Podfile")

        pod("powersync-sqlite-core") {
            linkOnly = true
        }

        framework {
            baseName = "composeApp"
            isStatic = true
            export("com.powersync:core")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("com.powersync:core")
            implementation("com.powersync:connector-supabase")
            implementation("com.powersync:compose")
            implementation(projectLibs.bundles.sqldelight)
            implementation(projectLibs.kotlinx.datetime)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(projectLibs.compose.ui.tooling.preview)
            implementation(projectLibs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.powersync.demos"
    compileSdk =
        projectLibs.versions.android.compileSdk
            .get()
            .toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.powersync.demos"
        minSdk =
            projectLibs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            projectLibs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

val localProperties =
    Properties().apply {
        try {
            load(rootProject.file("local.properties").reader())
        } catch (ignored: java.io.IOException) {
            throw Error("local.properties file not found")
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
            } else {
                buildConfigField(STRING, name, propValue)
            }
        }

        stringConfigField("POWERSYNC_URL")
        stringConfigField("SUPABASE_URL")
        stringConfigField("SUPABASE_ANON_KEY")
        stringConfigField("SUPABASE_USER_EMAIL")
        stringConfigField("SUPABASE_USER_PASSWORD")
    }
}
