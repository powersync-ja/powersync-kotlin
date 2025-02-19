import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

tasks.compileJava {
    options.release = 21
}

kotlin {
    explicitApi()

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.mavenPublishPlugin)
}