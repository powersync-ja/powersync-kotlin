package com.powersync

import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

public interface PowerSyncBuilder {

    public fun build(): PowerSyncDatabase

    /**
     * By default [PowerSyncDatabase] will open a global scope for management of shared processes, if instead you'd like to control
     * the scope that sharing/multicasting happens in you can pass a @param [scope]
     */
    public fun scope(scope: CoroutineScope): PowerSyncBuilder

    public companion object {

        public const val DEFAULT_DB_FILENAME: String = "powersync.db"
        public fun from(factory: DatabaseDriverFactory, schema: Schema): PowerSyncBuilder {
            return from(factory, schema, DEFAULT_DB_FILENAME)
        }

        public fun from(
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

    @OptIn(DelicateCoroutinesApi::class)
    override fun build(): PowerSyncDatabase = PowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope ?: GlobalScope
    )
}
