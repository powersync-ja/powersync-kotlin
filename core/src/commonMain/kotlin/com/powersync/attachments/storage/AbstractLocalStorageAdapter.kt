package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorageAdapter

@OptIn(ExperimentalMultiplatform::class)
public expect abstract class AbstractLocalStorageAdapter() : LocalStorageAdapter {
    override fun getUserStorageDirectory(): String
}
