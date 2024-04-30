plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlin.jvm)
}

gradlePlugin {
    // Define the plugin
    val sonatypeCentralUpload by plugins.creating {
        id = "com.powersync.plugins.sonatype"
        implementationClass = "com.powersync.plugins.sonatype.SonatypeCentralUploadPlugin"
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.mavenPublishPlugin)
}