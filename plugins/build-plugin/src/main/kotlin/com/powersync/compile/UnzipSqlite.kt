package com.powersync.compile

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputDirectory

/**
 * A cacheable [Copy] task providing a typed provider for the output directory.
 */
@CacheableTask
abstract class UnzipSqlite: Copy() {
    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    fun unzipSqlite(src: FileTree, dir: Provider<Directory>, filter: String? = "*/sqlite3.*") {
        from(
            src.matching {
                filter?.let { include(it) }
                exclude {
                    it.isDirectory
                }
                eachFile {
                    this.path = this.name
                }
            },
        )

        into(dir)
        destination.set(dir)
    }
}
