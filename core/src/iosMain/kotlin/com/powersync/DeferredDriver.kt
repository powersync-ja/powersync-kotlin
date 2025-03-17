package com.powersync

/**
 * In some cases we require an instance of a driver for hook registrations
 * before the driver has been instantiated.
 */
internal class DeferredDriver {
    private var driver: PsSqlDriver? = null

    fun setDriver(driver: PsSqlDriver) {
        this.driver = driver
    }

    fun updateTableHook(tableName: String) {
        println("table updated $tableName")
        driver?.updateTable(tableName)
    }

    fun onTransactionCommit(success: Boolean) {
        driver?.also { driver ->
            // Only clear updates on rollback
            // We manually fire updates when a transaction ended
            if (!success) {
                driver.clearTableUpdates()
            }
        }
    }
}
