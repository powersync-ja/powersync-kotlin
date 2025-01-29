package com.powersync.persistence.driver

import app.cash.sqldelight.db.SqlCursor

public interface ColNamesSqlCursor: SqlCursor {
    public fun columnName(index: Int): String?

    public val columnCount: Int
}