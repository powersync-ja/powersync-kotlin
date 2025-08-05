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
    if (!useReleasedVersions) {
        configurations.all {
            // https://docs.gradle.org/current/userguide/resolution_rules.html#sec:conditional-dependency-substitution
            resolutionStrategy.dependencySubstitution.all {
                requested.let {
                    if (it is ModuleComponentSelector && it.group == "com.powersync") {
                        val targetProject = findProject(":${it.module}")
                        if (targetProject != null) {
                            useTarget(targetProject)
                        }
                    }
                }
            }
        }
    }
}
