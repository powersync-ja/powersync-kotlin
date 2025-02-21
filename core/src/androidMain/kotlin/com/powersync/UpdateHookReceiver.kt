package com.powersync

internal interface UpdateHookReceiver {
    // These methods are used by native code
    @Suppress("unused")
    fun onTableUpdate(tableName: String)

    @Suppress("unused")
    fun onTransactionResult(commit: Boolean)
}
