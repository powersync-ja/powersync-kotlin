package com.powersync.compile

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "not worth caching")
abstract class CreateSqliteCInterop: DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val definitionFile: RegularFileProperty

    @TaskAction
    fun run() {
        val archive = archiveFile.get().asFile
        val parent = archive.parentFile

        definitionFile.get().asFile.writeText("""
            package = com.powersync.sqlite3
            
            linkerOpts.linux_x64 = -lpthread -ldl
            linkerOpts.macos_x64 = -lpthread -ldl
            staticLibraries=${archive.name}
            libraryPaths=${parent.relativeTo(project.layout.projectDirectory.asFile.canonicalFile)}
            """.trimIndent(),
        )
    }
}
