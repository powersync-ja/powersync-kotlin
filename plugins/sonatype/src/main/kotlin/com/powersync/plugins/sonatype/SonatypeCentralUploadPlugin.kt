package com.powersync.plugins.sonatype

import com.powersync.plugins.sonatype.internal.DefaultSonatypeCentralExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip

class SonatypeCentralUploadPlugin : Plugin<Project> {

    companion object {
        const val GROUP = "publishing"
        const val REPO_DIR = "sonatypeLocal"
        const val BUNDLE_DIR = "sonatypeBundles"

        const val PUBLISH_TASK_NAME = "publishAllPublicationsToSonatypeRepository"
        const val PUBLISH_LOCAL_TASK_NAME = "publishAllPublicationsToSonatypeLocalRepository"
        const val COMPONENT_BUNDLE_TASK_NAME = "generateSonatypeComponentBundle"
    }

    fun setupPlugins(log: Logger, target: Project) {
        log.info("Applying the `gradle-maven-publish` plugin")
        target.plugins.apply(MavenPublishPlugin::class.java)

        target.gradlePublishing.repositories.maven { repo ->
            repo.name = REPO_DIR
            repo.url = target.uri(target.layout.buildDirectory.dir(REPO_DIR))
        }

        log.info("Setting up the `:${PUBLISH_LOCAL_TASK_NAME}` task")

    }

    fun setupExtension(
        log: Logger,
        target: Project
    ): DefaultSonatypeCentralExtension? {
        log.info("Setting up the `centralPortal` extension")
        return target.extensions.create("centralPortal", DefaultSonatypeCentralExtension::class.java)
    }

    fun setupBundleTask(
        log: Logger,
        target: Project
    ): AbstractArchiveTask {
        log.info("Setting up the `:${COMPONENT_BUNDLE_TASK_NAME}` task")
        val generateBundleTask = target.tasks.create(COMPONENT_BUNDLE_TASK_NAME, Zip::class.java)
        generateBundleTask.group = GROUP
        generateBundleTask.description = "Creates a bundle for Sonatype Central Portal publishing."
        generateBundleTask.archiveClassifier.set("bundle")
        generateBundleTask.dependsOn(PUBLISH_LOCAL_TASK_NAME)

        val sonatypeCentralUploadDir =
            target.file(target.layout.buildDirectory.dir(REPO_DIR))

        generateBundleTask.from(sonatypeCentralUploadDir){
            it.exclude { fileTreeElement ->
                fileTreeElement.relativePath.lastName.startsWith("maven-metadata")
            }
        }
        val bundlesDir = target.layout.buildDirectory.dir(BUNDLE_DIR)
        generateBundleTask.destinationDirectory.set(bundlesDir)

        return generateBundleTask
    }

    fun setupPublishToCentralPortalTask(
        log: Logger,
        target: Project,
        bundleTask: AbstractArchiveTask
    ) {
        log.info("Setting up the `:${PUBLISH_TASK_NAME}` task")
        val publishToCentralPortalTask = target.tasks.create(
            PUBLISH_TASK_NAME,
            PublishToCentralPortal::class.java
        )
        publishToCentralPortalTask.group = GROUP
        publishToCentralPortalTask.description = "Publishes the bundle to Sonatype Central Portal"
        publishToCentralPortalTask.dependsOn(bundleTask)
        publishToCentralPortalTask.upload(bundleTask);
    }

    override fun apply(project: Project) {
        val log = project.logger

        setupPlugins(log, project)

        setupExtension(log, project)

        val bundleTask = setupBundleTask(
            log, project
        )

        setupPublishToCentralPortalTask(log, project, bundleTask);

    }
}
