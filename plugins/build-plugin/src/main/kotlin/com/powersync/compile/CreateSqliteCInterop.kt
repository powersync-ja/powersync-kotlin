package com.powersync.compile

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "not worth caching")
abstract class CreateSqliteCInterop: DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val definitionFile: RegularFileProperty

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun run() {
        val archive = archiveFile.get().asFile
        val parent = archive.parentFile

        definitionFile.get().asFile.writeText("""
            package = com.powersync.sqlite3
            
            linkerOpts.linux_x64 = -lpthread -ldl
            linkerOpts.macos_x64 = -lpthread -ldl
            staticLibraries=${archive.name}
            libraryPaths=${parent.relativeTo(layout.projectDirectory.asFile.canonicalFile)}
            """.trimIndent(),
        )
    }
}

@DisableCachingByDefault(because = "not worth caching")
abstract class CreatePowerSyncSqliteCoreCInterop: DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val definitionFile: RegularFileProperty

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun run() {
        val archive = archiveFile.get().asFile
        val parent = archive.parentFile

        definitionFile.get().asFile.writeText("""
            package = com.powersync.internal.core_extension
            
            linkerOpts.linux_x64 = -lpthread -ldl
            linkerOpts.macos_x64 = -lpthread -ldl
            staticLibraries=${archive.name}
            libraryPaths=${parent.relativeTo(layout.projectDirectory.asFile.canonicalFile)}
            ---
            
            int sqlite3_powersync_init(void* db, void* err_msg, void* api);
            """.trimIndent(),
        )
    }
}
