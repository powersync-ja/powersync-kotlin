/**
 * This file provides utility functions for setting up Full-Text Search (FTS)
 * using the FTS5 extension with PowerSync in a Kotlin Multiplatform project.
 * It mirrors the functionality of the fts_setup.dart file from the PowerSync
 * Flutter examples.
 *
 * Note: FTS5 support depends on the underlying SQLite engine used by the
 * PowerSync KMP SDK on each target platform. Ensure FTS5 is enabled/available.
 */
@file:JvmName("FtsSetupKt")

package com.powersync.demos.fts

import com.powersync.PowerSyncDatabase
import com.powersync.db.WriteTransaction
import com.powersync.db.schema.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class `FtsSetup.kt` {
}