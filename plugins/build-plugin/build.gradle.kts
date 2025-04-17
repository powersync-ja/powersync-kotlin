plugins {
    `kotlin-dsl` // Enables Kotlin DSL for writing Gradle build logic
}

gradlePlugin {
      // Define the plugin
    val sonatypeCentralUpload by plugins.creating {
        id = "com.powersync.plugins.sharedbuild"
        implementationClass = "com.powersync.plugins.sharedbuild.SharedBuildPlugin"
    }
}

dependencies {
    implementation(libs.gradle.download.task)
    implementation(libs.kotlin.gradle.plugin)
}
