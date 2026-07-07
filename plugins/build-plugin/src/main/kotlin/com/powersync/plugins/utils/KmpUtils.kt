package com.powersync.plugins.utils

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

@OptIn(ExperimentalWasmDsl::class)
public fun KotlinMultiplatformExtension.powersyncTargets(
    native: Boolean = true,
    jvm: Boolean = true,
    android: (KotlinMultiplatformAndroidLibraryTarget.() -> Unit)? = null,
    includeTargetsWithoutComposeSupport: Boolean = true,
    watchOS: Boolean = true,
    legacyJavaSupport: Boolean = true,
    web: Boolean = false,
) {
    if (jvm) {
        android?.let { configureAndroid ->
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
                val versionCatalogs = project.extensions.getByName("versionCatalogs") as VersionCatalogsExtension
                val catalog = versionCatalogs.named("libs")
                compileSdk = catalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
                minSdk = catalog.findVersion("android-minSdk").get().requiredVersion.toInt()

                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }

                configureAndroid()
            }
        }

        jvm {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                if (legacyJavaSupport) {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                    // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
                    freeCompilerArgs.add("-Xjdk-release=8")
                } else {
                    jvmTarget.set(JvmTarget.JVM_11)
                    freeCompilerArgs.add("-Xjdk-release=11")
                }
            }
        }
    }

    if (native) {
        iosArm64()
        iosSimulatorArm64()

        if (includeTargetsWithoutComposeSupport) {
            macosArm64()

            tvosSimulatorArm64()
            tvosArm64()

            if (watchOS) {
                watchosDeviceArm64() // aarch64-apple-watchos
                watchosArm64() // arm64_32-apple-watchos

                watchosSimulatorArm64() // aarch64-apple-watchos-simulator
            }
        }
    }

    if (web) {
        js {
            browser()
        }

        wasmJs {
            browser()
        }
    }
}
