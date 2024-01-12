enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io" )
    }
    versionCatalogs {
        create("projectLibs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include(":composeApp")

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("co.powersync:core"))
            .using(project(":core")).because("we want to auto-wire up sample dependency")
        substitute(module("co.powersync:connectors"))
            .using(project(":connectors")).because("we want to auto-wire up sample dependency")
    }
}
