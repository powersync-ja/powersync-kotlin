import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getLocalProperty(
    key: String,
    defaultValue: String,
): String = localProperties.getProperty(key, defaultValue)

android {
    namespace = "com.powersync.androidexample"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.powersync.androidexample"
        minSdk = 24
        targetSdk = 35
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
    kotlinOptions {
        jvmTarget = "1.8"
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

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // To use a fixed version, replace "latest.release" with the latest version available at
    // https://central.sonatype.com/artifact/com.powersync/core
    implementation("com.powersync:core:latest.release")
    implementation("com.powersync:connector-supabase:latest.release")
    implementation("com.powersync:compose:latest.release")
    implementation(libs.uuid)
    implementation(libs.kermit)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.activity:activity-ktx:1.9.0")
}
