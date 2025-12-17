package com.powersync.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

internal class PowerSyncVersionPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.group = project.property("GROUP") as String
        project.version = project.property("LIBRARY_VERSION") as String
    }
}
