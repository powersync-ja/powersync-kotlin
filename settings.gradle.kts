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
include(":core-test")
include(":connectors:supabase")

include(":dialect")
include(":persistence")
include(":PowerSyncKotlin")

include(":compose")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
