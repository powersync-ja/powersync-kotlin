/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Darwin targets
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    
    macosX64()
    macosArm64()
    
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()
    
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()

    // Use the default hierarchy template - appleMain covers all Darwin targets
    sourceSets {
        // appleMain is automatically created by the default hierarchy template
        // and includes all Apple/Darwin targets
        appleMain {
            kotlin.srcDir("darwin/src")
            dependencies {
                api(libs.ktor.client.core)
                api(libs.ktor.network.tls)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        
        appleTest {
            kotlin.srcDir("darwin/test")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.ktor.client.logging)
            }
        }
    }
}
