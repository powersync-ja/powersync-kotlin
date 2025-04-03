package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorageAdapter

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect abstract class AbstractLocalStorageAdapter() : LocalStorageAdapter {
    override fun getUserStorageDirectory(): String
}
