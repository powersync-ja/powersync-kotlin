package com.powersync.attachments.storage

import android.os.Environment
import com.powersync.attachments.LocalStorageAdapter

public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = Environment.getDataDirectory().absolutePath
}
