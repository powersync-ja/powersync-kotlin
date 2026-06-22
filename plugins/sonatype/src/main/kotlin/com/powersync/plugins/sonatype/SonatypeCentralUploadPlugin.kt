package com.powersync.plugins.sonatype

import com.powersync.plugins.PowerSyncVersionPlugin
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.plugins.InvalidPluginException

public class SonatypeCentralUploadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(PowerSyncVersionPlugin::class.java)

        project.logger.info("Applying the `gradle-maven-publish` plugin")
        project.plugins.apply(MavenPublishPlugin::class.java)

        if (!publishedProjects.contains(project.path)) {
            throw InvalidPluginException("To publish project ${project.path}, add it to SonatypeCentralUploadPlugin.publishedProjects")
        }

        val extension = project.extensions.create(
            SonatypeCentralExtension.NAME,
            SonatypeCentralExtension::class.java,
            project
        )

        // The publishing plugin would apply Dokka to upload docs by default, but we only really apply Dokka on the root
        // project, so this breaks the release flow.
        if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            project.extensions.getByType(MavenPublishBaseExtension::class.java).configure(KotlinMultiplatform(
                javadocJar = JavadocJar.Empty(),
            ))
        } else {
            project.extensions.getByType(MavenPublishBaseExtension::class.java).configure(
                AndroidSingleVariantLibrary(
                javadocJar = JavadocJar.Empty(),
            ))
        }

        extension.apply()
    }

    public companion object {
        public val publishedProjects: List<String> = listOf(
            ":common",
            ":compose",
            ":core",
            ":integrations:room",
            ":integrations:sqldelight",
            ":integrations:supabase",
            ":internal:sqlite3mcandroid",
            ":sqlite3multipleciphers",
            ":static-sqlite-driver"
        )
    }
}
