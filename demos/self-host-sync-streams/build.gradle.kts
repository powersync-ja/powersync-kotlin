plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinter)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        optIn.add("com.powersync.ExperimentalPowerSyncAPI")
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.integrations.sqldelight)
    implementation(projects.compose)

    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kmp.lifecycle.compose)
}

sqldelight {
    databases {
        linkSqlite.set(false)

        create("TodoDatabase") {
            packageName.set("com.powersync.integrations.sqldelight")
            generateAsync.set(true)
            deriveSchemaFromMigrations.set(false)
            dialect(libs.sqldelight.dialect.sqlite38)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.powersync.demo.self_host.MainKt"
    }
}
