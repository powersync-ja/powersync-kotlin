package co.powersync.db.internal

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.SqlDriver

class SqlTransactor(driver: SqlDriver) : SuspendingTransacterImpl(driver) {
}