package com.powersync.plugins.sonatype

import org.gradle.api.Project
import org.gradle.api.tasks.Delete

public abstract class SonatypeCentralExtension(
    private val project: Project
) {
    public companion object {
        public const val NAME: String = "sonatypePublishing"
        public const val GROUP: String = "publishing"
        public const val REPO_DIR: String = "sonatypeLocal"

        public const val PUBLISH_LOCAL_TASK_NAME: String = "publishAllPublicationsToSonatypeLocalRepository"
        public const val CLEAN_LOCAL_TASK_NAME: String = "cleanSonatypeLocal"
    }

    internal fun apply() {
        val log = project.logger
        log.info("Setting up the `:${PUBLISH_LOCAL_TASK_NAME}` task")
        val bundleDir = project.layout.buildDirectory.dir(REPO_DIR)
        project.gradlePublishing.repositories.maven { repo ->
            repo.name = REPO_DIR
            repo.url = project.uri(bundleDir)
        }

        project.tasks.register(CLEAN_LOCAL_TASK_NAME, Delete::class.java) { task ->
            task.group = GROUP
            task.description = "Deletes the local $REPO_DIR directory for this project"
            task.delete(bundleDir)
        }

        val config = project.configurations.create("sonatypePublishingBundleConfiguration") { config ->
            config.isCanBeResolved = false
        }
        val publishingTask = project.tasks.named(PUBLISH_LOCAL_TASK_NAME)

        // intermediate task because publishAllPublicationsToSonatypeLocalRepository does not declare
        // task outputs.
        val markAsPublishedTask = project.tasks.register("generateSonatypeArtifact") { task ->
            task.dependsOn(publishingTask)
            task.outputs.dir(bundleDir)
        }

        project.artifacts.add(
            config.name,
            markAsPublishedTask
        )
    }
}
