package com.powersync

import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.internal.PsInternalDatabase
import com.powersync.db.schema.Schema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

interface PowerSyncBuilder {

    fun build(): PowerSyncDatabase

    /**
     * By default [PowerSyncDatabase] will open a global scope for management of shared processes, if instead you'd like to control
     * the scope that sharing/multicasting happens in you can pass a @param [scope]
     */
    fun scope(scope: CoroutineScope): PowerSyncBuilder

    companion object {

        const val DEFAULT_DB_FILENAME = "powersync.db"
        fun from(factory: DatabaseDriverFactory, schema: Schema): PowerSyncBuilder {
            return from(factory, schema, DEFAULT_DB_FILENAME)
        }

        fun from(
            factory: DatabaseDriverFactory,
            schema: Schema,
            dbFilename: String
        ): PowerSyncBuilder {
            return PowerSyncBuilderImpl(factory, schema, dbFilename)
        }
    }
}

internal class PowerSyncBuilderImpl(
    private val factory: DatabaseDriverFactory,
    private val schema: Schema,
    private val dbFilename: String
) : PowerSyncBuilder {

    private var scope: CoroutineScope? = null

    override fun scope(scope: CoroutineScope): PowerSyncBuilder {
        this.scope = scope
        return this
    }

    override fun build(): PowerSyncDatabase = PowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope ?: GlobalScope
    )
}
