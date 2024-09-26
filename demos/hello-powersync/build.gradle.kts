plugins {
    alias(projectLibs.plugins.androidApplication) apply false
    alias(projectLibs.plugins.androidLibrary) apply false
    alias(projectLibs.plugins.jetbrainsCompose) apply false
    alias(projectLibs.plugins.compose.compiler) apply false
    alias(projectLibs.plugins.kotlinMultiplatform) apply false
    alias(projectLibs.plugins.cocoapods) apply false
    alias(libs.plugins.buildKonfig) apply false
}