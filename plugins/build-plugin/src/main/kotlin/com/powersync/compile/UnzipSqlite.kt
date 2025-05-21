package com.powersync.compile

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputDirectory

/**
 * A cacheable [Copy] task providing typed providers for the emitted [sqlite3C] and [sqlite3H]
 * files, making them easier to access in other tasks.
 */
@CacheableTask
abstract class UnzipSqlite: Copy() {
    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    fun intoDirectory(dir: Provider<Directory>) {
        into(dir)
        destination.set(dir)
    }
}
