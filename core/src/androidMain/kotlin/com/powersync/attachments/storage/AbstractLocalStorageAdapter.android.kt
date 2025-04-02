package com.powersync.attachments.storage

import android.os.Environment
import com.powersync.attachments.LocalStorageAdapter

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = Environment.getDataDirectory().absolutePath
}
