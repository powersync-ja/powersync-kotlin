package com.powersync.plugins.sonatype

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.bundling.Zip

public abstract class SonatypeCentralExtension(
    private val project: Project
) {
    @get:Optional
    public val username: Property<String?> = project.objects.property(String::class.java)

    @get:Optional
    public val password: Property<String?> = project.objects.property(String::class.java)

    public companion object {
        public const val NAME: String = "sonatypePublishing"
        public const val GROUP: String = "publishing"
        public const val REPO_DIR: String = "sonatypeLocal"
        public const val UNIFIED_REPO_DIR: String = "testingLocal"
        public const val BUNDLE_DIR: String = "sonatypeBundles"

        public const val PUBLISH_TASK_NAME: String = "publishAllPublicationsToSonatypeRepository"
        public const val PUBLISH_LOCAL_TASK_NAME: String = "publishAllPublicationsToSonatypeLocalRepository"
        public const val COMPONENT_BUNDLE_TASK_NAME: String = "generateSonatypeComponentBundle"

        // This should be stored in your ~/.gradle/gradle.properties
        public const val SONATYPE_USERNAME_KEY: String = "centralPortal.username"
        public const val SONATYPE_PASSWORD_KEY: String = "centralPortal.password"
    }

    internal fun apply() {
        val log = project.logger
        log.info("Setting up the `:${PUBLISH_LOCAL_TASK_NAME}` task")
        project.gradlePublishing.repositories.maven { repo ->
            repo.name = REPO_DIR
            repo.url = project.uri(project.layout.buildDirectory.dir(REPO_DIR))
        }
        project.gradlePublishing.repositories.maven { repo ->
            repo.name = UNIFIED_REPO_DIR
            repo.url = project.uri(project.rootProject.layout.buildDirectory.dir(UNIFIED_REPO_DIR))
        }

        log.info("Setting up the `:${COMPONENT_BUNDLE_TASK_NAME}` task")
        val sonatypeCentralUploadDir =
            project.file(project.layout.buildDirectory.dir(REPO_DIR))

        project.tasks.create(COMPONENT_BUNDLE_TASK_NAME, Zip::class.java) {
            it.group = GROUP
            it.description = "Creates a bundle for Sonatype Central Portal publishing."
            it.archiveClassifier.set("bundle")
            it.dependsOn(project.tasks.named(PUBLISH_LOCAL_TASK_NAME))
            it.from(sonatypeCentralUploadDir) { cp ->
                cp.exclude { fileTreeElement ->
                    fileTreeElement.relativePath.lastName.startsWith("maven-metadata")
                }
            }
            it.destinationDirectory.set(project.layout.buildDirectory.dir(BUNDLE_DIR))
        }

        log.info("Setting up the `:${PUBLISH_TASK_NAME}` task")
        project.tasks.create(PUBLISH_TASK_NAME, PublishToCentralPortalTask::class.java) {
            it.group = GROUP
            it.description = "Publishes the bundle to Sonatype Central Portal"
            it.dependsOn(project.tasks.named(COMPONENT_BUNDLE_TASK_NAME))
        }
    }
}
