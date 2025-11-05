package com.powersync.encryption

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

public sealed interface Key {
    public class Passphrase(public val passphrase: String) : Key
    // TODO: Add raw key api
}

internal fun SQLiteConnection.encryptOrClose(key: Key) {
    try {
        when (key) {
            is Key.Passphrase -> {
                val escaped = key.passphrase.replace("'", "''")
                execSQL("pragma key='$escaped'")
            }
        }
    } catch (e: Exception) {
        close()
        throw e
    }
}