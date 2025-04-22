package com.powersync.plugins.utils

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions

public fun KotlinTargetContainerWithPresetFunctions.powersyncTargets(
    native: Boolean = true,
    jvm: Boolean = true,
    includeTargetsWithoutComposeSupport: Boolean = true,
) {
    if (jvm) {
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
    }

    if (native) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()

        if (includeTargetsWithoutComposeSupport) {
            macosX64()
            macosArm64()
        }
    }
}
