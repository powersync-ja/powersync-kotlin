package com.powersync.integrations.room

import androidx.room3.Transactor
import com.powersync.db.driver.SQLiteConnectionLease

internal actual suspend fun Transactor.asConnectionLease(): SQLiteConnectionLease = WebRoomTransactionLease(this)

private class WebRoomTransactionLease(
    transactor: Transactor,
) : RoomTransactionLease(transactor)
