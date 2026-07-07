package com.powersync.db.internal

import com.powersync.db.driver.SQLiteConnectionLease

internal actual fun createTransactionImpl(original: SQLiteConnectionLease): PowerSyncTransaction = TransactionImpl(original)

private class TransactionImpl(
    lease: SQLiteConnectionLease,
) : BasePowerSyncTransactionImpl(lease)
