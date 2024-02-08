package com.powersync.plugins.sonatype

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

inline val Project.gradlePublishing: PublishingExtension
    get() = extensions.getByType(PublishingExtension::class.java)

internal fun Project.findOptionalProperty(propertyName: String) = findProperty(propertyName)?.toString()