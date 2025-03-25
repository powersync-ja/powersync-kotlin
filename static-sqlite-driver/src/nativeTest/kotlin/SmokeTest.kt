import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.createDatabaseManager
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun canUseSqlite() {
        val manager = createDatabaseManager(DatabaseConfiguration(
            name = "test",
            version = 1,
            create = {},
            inMemory = true,
        ))
        val db = manager.createSingleThreadedConnection()
        val stmt = db.createStatement("SELECT sqlite_version();")
        val cursor = stmt.query()

        assertEquals(true, cursor.next())
        db.close()
    }
}
