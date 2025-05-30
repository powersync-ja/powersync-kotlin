package com.powersync.connector.supabase

import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema

/**
 * Extension function to configure a SupabaseConnector with a PowerSync database schema.
 * This enables automatic conflict resolution for upsert operations based on unique constraints
 * defined in the schema.
 * 
 * Example:
 * ```
 * val connector = SupabaseConnector(supabaseUrl, supabaseKey, powerSyncUrl)
 * connector.configureWithDatabase(database)
 * ```
 */
public fun SupabaseConnector.configureWithDatabase(database: PowerSyncDatabase) {
    val schema = database.schema
    this.setSchema(schema)
}

/**
 * Create a SupabaseConnector with schema configuration.
 * 
 * Example:
 * ```
 * val schema = Schema(
 *     Table(
 *         name = "users",
 *         columns = listOf(
 *             Column.text("email"),
 *             Column.text("username")
 *         ),
 *         indexes = listOf(
 *             Index.unique("idx_email", "email")
 *         )
 *     )
 * )
 * 
 * val connector = SupabaseConnector.withSchema(
 *     supabaseUrl = "https://example.supabase.co",
 *     supabaseKey = "your-key",
 *     powerSyncEndpoint = "https://example.powersync.com",
 *     schema = schema
 * )
 * ```
 */
public fun SupabaseConnector.Companion.withSchema(
    supabaseUrl: String,
    supabaseKey: String,
    powerSyncEndpoint: String,
    storageBucket: String? = null,
    schema: Schema
): SupabaseConnector {
    val connector = SupabaseConnector(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
        powerSyncEndpoint = powerSyncEndpoint,
        storageBucket = storageBucket
    )
    connector.setSchema(schema)
    return connector
}

// Add companion object to SupabaseConnector for the extension function
public val SupabaseConnector.Companion: SupabaseConnectorCompanion
    get() = SupabaseConnectorCompanion

public object SupabaseConnectorCompanion