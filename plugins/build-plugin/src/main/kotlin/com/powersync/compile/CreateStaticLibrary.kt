package com.powersync.compile

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class CreateStaticLibrary: DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val objects: ConfigurableFileCollection

    @get:OutputFile
    abstract val staticLibrary: RegularFileProperty

    @get:Inject
    abstract val providers: ProviderFactory

    @TaskAction
    fun run() {
        providers.exec {
            executable = "ar"
            args("rc", staticLibrary.get().asFile.absolutePath)
            for (file in objects.files) {
                args(file.absolutePath)
            }
        }.result.get()
    }
}
