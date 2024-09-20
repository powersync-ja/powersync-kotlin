pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://jitpack.io")
        mavenCentral()
    }
}

rootProject.name = "AndroidExample"
include(":app")

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.powersync:core"))
            .using(project(":core")).because("we want to auto-wire up sample dependency")
        substitute(module("com.powersync:connector-supabase"))
            .using(project(":connectors:supabase"))
            .because("we want to auto-wire up sample dependency")
        substitute(module("com.powersync:compose"))
            .using(project(":compose"))
            .because("we want to auto-wire up sample dependency")
    }
}