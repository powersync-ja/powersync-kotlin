package com.powersync.db

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
