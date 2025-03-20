plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.cocoapods) apply false
    alias(libs.plugins.kmmbridge) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.grammarKitComposer) apply false
    alias(libs.plugins.mavenPublishPlugin) apply false
    alias(libs.plugins.downloadPlugin) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.keeper) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        // Repo for the backported Android IntelliJ Plugin by Jetbrains used in Ultimate
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/")
    }

    configurations.configureEach {
        exclude(group = "com.jetbrains.rd")
        exclude(group = "com.github.jetbrains", module = "jetCheck")
        exclude(group = "com.jetbrains.intellij.platform", module = "wsl-impl")
        exclude(group = "org.roaringbitmap")
        exclude(group = "com.jetbrains.infra")
        exclude(group = "org.jetbrains.teamcity")
        exclude(group = "org.roaringbitmap")
        exclude(group = "ai.grazie.spell")
        exclude(group = "ai.grazie.model")
        exclude(group = "ai.grazie.utils")
        exclude(group = "ai.grazie.nlp")

        // We have a transitive dependency on this due to Kermit, but need the fixed version to support Java 8
        resolutionStrategy.force("co.touchlab:stately-collections:${libs.versions.stately.get()}")
    }
}
subprojects {
    val GROUP: String by project
    val LIBRARY_VERSION: String by project

    group = GROUP
    version = LIBRARY_VERSION
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
