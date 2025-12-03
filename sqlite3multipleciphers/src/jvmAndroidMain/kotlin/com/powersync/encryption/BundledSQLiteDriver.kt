/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("BundledSQLiteDriverKt")

package com.powersync.encryption

import androidx.sqlite.SQLiteConnection
import com.powersync.PersistentConnectionFactory
import com.powersync.resolvePowerSyncLoadableExtensionPath

public abstract class BundledSQLiteDriver internal constructor(
    private val key: Key,
) : PersistentConnectionFactory {
    private fun open(
        fileName: String,
        flags: Int,
    ): SQLiteConnection {
        ensureJniLibraryLoaded()

        val address = nativeOpen(fileName, flags)
        val connection = BundledSQLiteConnection(address)
        try {
            connection.loadExtension(resolvePowerSyncLoadableExtensionPath()!!, "sqlite3_powersync_init")
        } catch (th: Throwable) {
            connection.close()
            throw th
        }

        return connection
    }

    override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = open(path, openFlags).also { it.encryptOrClose(key) }

    override fun openInMemoryConnection(): SQLiteConnection = open(":memory:", 2)
}

internal expect fun ensureJniLibraryLoaded()

private external fun nativeOpen(
    name: String,
    openFlags: Int,
): Long
