package com.powersync.plugins.sonatype

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional

interface SonatypeCentralExtension {

    @get:Optional
    val username: Property<String?>

    @get:Optional
    val password: Property<String?>
}

