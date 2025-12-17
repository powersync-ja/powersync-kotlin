package com.powersync.plugins.sonatype

import com.powersync.plugins.PowerSyncVersionPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

internal class SonatypeCentralUploadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(PowerSyncVersionPlugin::class.java)

        project.logger.info("Applying the `gradle-maven-publish` plugin")
        project.plugins.apply(MavenPublishPlugin::class.java)

        val extension = project.extensions.create(
            SonatypeCentralExtension.NAME,
            SonatypeCentralExtension::class.java,
            project
        )

        // The publishing plugin would apply Dokka to upload docs by default, but we only really apply Dokka on the root
        // project, so this breaks the release flow.
        @Suppress("UnstableApiUsage")
        project.extensions.getByType(MavenPublishBaseExtension::class.java).configure(KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
        ))

        extension.apply()
    }
}
