package com.powersync.db.schema

/**
 * Example demonstrating how to define tables with unique constraints
 * and use them with the Supabase connector for proper upsert behavior.
 * 
 * ```kotlin
 * // Define a schema with unique constraints
 * val schema = Schema(
 *     Table(
 *         name = "users",
 *         columns = listOf(
 *             Column.text("email"),
 *             Column.text("username"),
 *             Column.text("full_name"),
 *             Column.integer("age")
 *         ),
 *         indexes = listOf(
 *             // Single column unique constraint
 *             Index.unique("idx_email", "email"),
 *             // Another single column unique constraint
 *             Index.unique("idx_username", "username"),
 *             // Regular non-unique index for performance
 *             Index.ascending("idx_age", listOf("age"))
 *         )
 *     ),
 *     Table(
 *         name = "products",
 *         columns = listOf(
 *             Column.text("sku"),
 *             Column.text("name"),
 *             Column.real("price"),
 *             Column.text("category")
 *         ),
 *         indexes = listOf(
 *             // Composite unique constraint on multiple columns
 *             Index.unique("idx_sku_category", listOf("sku", "category"))
 *         )
 *     )
 * )
 * 
 * // Initialize PowerSync database with the schema
 * val database = PowerSyncDatabase(
 *     schema = schema,
 *     // ... other configuration
 * )
 * 
 * // Configure Supabase connector with the schema
 * val connector = SupabaseConnector(
 *     supabaseUrl = "https://your-project.supabase.co",
 *     supabaseKey = "your-anon-key",
 *     powerSyncEndpoint = "https://your-instance.powersync.com"
 * )
 * connector.setSchema(schema)
 * 
 * // When the connector uploads data with PUT operations:
 * // - For the "users" table, conflicts will be resolved on the "email" column
 * //   (using the first unique index found)
 * // - For the "products" table, conflicts will be resolved on "sku,category"
 * // - Tables without unique constraints will use the default "id" column
 * 
 * // The Supabase connector automatically generates the appropriate
 * // upsert query with onConflict parameter based on your schema
 * ```
 */
internal object UniqueConstraintsExample