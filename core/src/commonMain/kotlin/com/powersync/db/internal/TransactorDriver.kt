package com.powersync.db.internal

import com.powersync.PsSqlDriver
import com.powersync.persistence.PsDatabase

/**
 * Wrapper for a driver which includes a dedicated transactor.
 */
internal class TransactorDriver(
    val driver: PsSqlDriver,
) {
    val transactor = PsDatabase(driver)
}
