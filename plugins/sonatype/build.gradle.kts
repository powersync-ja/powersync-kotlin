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

// The target release option here is the version of the JVM running the build by default, but Kotlin
// typically doesn't support the latest version yet, causing mismatch warnings. So, target the latest
// LTS java version to be safe.
val highestTargetVersion = JavaVersion.VERSION_21
val currentVersion = JavaVersion.current()
val targetVersion = minOf(highestTargetVersion, currentVersion)

java {
    targetCompatibility = targetVersion
}

kotlin {
    kotlin {
        explicitApi()

        compilerOptions {
            jvmTarget.set(JvmTarget.valueOf("JVM_${targetVersion.majorVersion}"))
        }
    }
}

dependencies {
    implementation(libs.mavenPublishPlugin)
}
