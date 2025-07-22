package com.powersync.db

import androidx.sqlite.execSQL
import com.powersync.internal.driver.JdbcConnection

internal fun JdbcConnection.loadExtensions(vararg extensions: Pair<String, String>) {
    connection.database.enable_load_extension(true)
    extensions.forEach { (path, entryPoint) ->
        val executed =
            connection.prepareStatement("SELECT load_extension(?, ?);").use { statement ->
                statement.setString(1, path)
                statement.setString(2, entryPoint)
                statement.execute()
            }
        check(executed) { "load_extension(\"${path}\", \"${entryPoint}\") failed" }
    }
    connection.database.enable_load_extension(false)
}

/**
 * Sets the user version pragma to `1` to continue the behavior of older versions of the PowerSync
 * SDK.
 */
internal fun JdbcConnection.setSchemaVersion() {
    execSQL("pragma user_version = 1")
}
