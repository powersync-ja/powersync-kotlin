import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties()
val localPropertiesFile = project.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getLocalProperty(
    key: String,
    defaultValue: String,
): String = localProperties.getProperty(key, defaultValue)

android {
    namespace = "com.powersync.androidexample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.powersync.androidexample"
        minSdk = 24
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", "\"${getLocalProperty("SUPABASE_URL", "")}\"")
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${getLocalProperty("SUPABASE_ANON_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_STORAGE_BUCKET",
            "\"${getLocalProperty("SUPABASE_STORAGE_BUCKET", "null")}\"",
        )
        buildConfigField("String", "POWERSYNC_URL", "\"${getLocalProperty("POWERSYNC_URL", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

val useReleasedVersions = getLocalProperty("USE_RELEASED_POWERSYNC_VERSIONS", "false") == "true"
if (useReleasedVersions) {
    configurations.all {
        // https://docs.gradle.org/current/userguide/resolution_rules.html#sec:conditional-dependency-substitution
        resolutionStrategy.dependencySubstitution.all {
            requested.let {
                if (it is ProjectComponentSelector) {
                    val projectPath = it.projectPath
                    // Translate a dependency of e.g. :core into com.powersync:core:latest.release,
                    // taking into account that the Supabase connector uses a custom name.
                    val moduleName = when (projectPath) {
                        ":connectors:supabase" -> "connector-supabase"
                        else -> it.projectPath.substring(1).replace(':', '-')
                    }

                    useTarget("com.powersync:${moduleName}:latest.release")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.supabase.client)
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // When adopting the PowerSync dependencies into your project, use the latest version available at
    // https://central.sonatype.com/artifact/com.powersync/core
    implementation(projects.core) // "com.powersync:core:latest.release"
    implementation(projects.connectors.supabase) // "com.powersync:connector-supabase:latest.release"
    implementation(projects.compose) // "com.powersync:compose:latest.release"
    implementation(libs.uuid)
    implementation(libs.kermit)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.ktx)
}
