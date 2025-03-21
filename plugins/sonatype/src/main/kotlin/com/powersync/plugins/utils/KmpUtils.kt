package com.powersync.plugins.utils

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions

public fun KotlinTargetContainerWithPresetFunctions.powersyncTargets(macos: Boolean = true) {
    androidTarget {
        publishLibraryVariants("release", "debug")

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
            freeCompilerArgs.add("-Xjdk-release=8")
        }
    }

    powersyncNativeTargets(macos=macos)
}

public fun KotlinTargetContainerWithPresetFunctions.powersyncNativeTargets(macos: Boolean = true) {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    if (macos) {
        macosX64()
        macosArm64()
    }
}
