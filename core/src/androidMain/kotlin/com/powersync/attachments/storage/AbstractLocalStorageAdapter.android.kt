package com.powersync.attachments.storage

import android.os.Environment
import android.os.Environment.DIRECTORY_DOCUMENTS
import com.powersync.attachments.LocalStorageAdapter

import okio.FileSystem

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.normalized().toString()
}
