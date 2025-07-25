package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.internal.driver.ConnectionListener

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory {
    internal fun openDatabase(
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean = false,
        listener: ConnectionListener?,
    ): SQLiteConnection
}
