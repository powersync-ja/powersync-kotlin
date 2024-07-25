import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Gradle complains when I attempt to re-use the plugin definition from the project toml. Something to do
    // with the way the sonatype module is set up I presume. Need to figure this out before merge
    id("org.jetbrains.kotlin.jvm")
    alias(projectLibs.plugins.jetbrainsCompose)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(compose.desktop.currentOs)
}

group = "com.powersync"
version = "1.0.0"

compose.desktop {
    application {
        mainClass = "com.powersync.demos.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PowerSync Demo"
            packageVersion = project.version as String
        }
    }
}