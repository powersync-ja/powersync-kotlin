package com.powersync

private typealias UpdateListener = (databaseName: String, tableName: String, operationType: Int, rowID: Long) -> Unit

class SqliteBindings {

    private var mUpdateListener: UpdateListener? = null

    private external fun registerUpdateListener(
        dbPtr: Long
    )

    private fun onUpdate(action: Int, databaseName: String, tableName: String, rowId: Long) {
        mUpdateListener?.invoke(databaseName, tableName, action, rowId)
    }

    fun registerUpdateHook(dbPtr: Long, listener: UpdateListener) {
        mUpdateListener = listener
        registerUpdateListener(dbPtr)
    }

    companion object {
        init {
            System.loadLibrary("powersync-sqlite")
        }

        @JvmStatic
        fun getInstance(): SqliteBindings {
            return SqliteBindings()
        }
    }
}