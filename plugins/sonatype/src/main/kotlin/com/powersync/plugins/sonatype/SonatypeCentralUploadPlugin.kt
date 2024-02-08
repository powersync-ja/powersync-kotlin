package com.powersync.plugins.sonatype

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.vanniktech.maven.publish.MavenPublishPlugin

class SonatypeCentralUploadPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.info("Applying the `gradle-maven-publish` plugin")
        project.plugins.apply(MavenPublishPlugin::class.java)

        val extension = project.extensions.create(SonatypeCentralExtension.NAME, SonatypeCentralExtension::class.java, project)
        extension.apply()
    }
}
