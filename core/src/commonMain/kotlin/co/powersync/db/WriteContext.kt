package co.powersync.db

interface WriteContext : ReadContext {

    /**
     * Execute a write query (INSERT, UPDATE, DELETE) and return the number of rows updated for an INSERT/DELETE/UPDATE.
     */
    suspend fun execute(sql: String, parameters: List<Any>? = listOf()): Long

}