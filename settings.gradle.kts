pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("plugins")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
    }
}

rootProject.name = "powersync-root"

include(":core")
include(":connectors:supabase")

include(":dialect")
include(":PowerSync")

include(":compose")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
