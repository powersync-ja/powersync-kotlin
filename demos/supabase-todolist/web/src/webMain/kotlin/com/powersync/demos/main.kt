package com.powersync.demos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import com.powersync.PowerSyncDatabase
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.schema.Schema
import com.powersync.web.DatabaseImplementation
import com.powersync.web.WebConnectionFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(::openPowerSyncDatabase)
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun openPowerSyncDatabase(schema: Schema): PowerSyncDatabase {
    val scope = GlobalScope
    val identifier = "demo.db"
    val factory = WebConnectionFactory(scope)

    return PowerSyncDatabase.opened(
        pool = factory.openPool { open(identifier, DatabaseImplementation.inMemoryShared) },
        scope = scope,
        schema = schema,
        identifier = identifier,
        logger = Logger(loggerConfigInit(platformLogWriter()))
    )
}
