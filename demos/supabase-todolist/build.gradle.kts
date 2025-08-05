import java.util.Properties

plugins {
    alias(libs.plugins.buildKonfig) apply false
}

val localProperties = Properties()
val localPropertiesFile = project.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val useReleasedVersions = localProperties.getProperty("USE_RELEASED_POWERSYNC_VERSIONS", "false") == "true"

subprojects {
    if (useReleasedVersions) {
        configurations.all {
            // https://docs.gradle.org/current/userguide/resolution_rules.html#sec:conditional-dependency-substitution
            resolutionStrategy.dependencySubstitution.all {
                requested.let {
                    if (it is ProjectComponentSelector) {
                        val projectPath = it.projectPath
                        if (projectPath.contains("demos")) {
                            // Project dependency within the demo, don't replace
                            return@let
                        }

                        // Translate a dependency of e.g. :core into com.powersync:core:latest.release,
                        // taking into account that the Supabase connector uses a custom name.
                        val moduleName = when (projectPath) {
                            ":connectors:supabase" -> "connector-supabase"
                            else -> it.projectPath.substring(1).replace(':', '-')
                        }

                        useTarget("com.powersync:${moduleName}:latest.release")
                    }
                }
            }
        }
    }
}

