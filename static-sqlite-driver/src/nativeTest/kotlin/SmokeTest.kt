import com.powersync.internal.driver.NativeDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun canUseSqlite() {
        val db = NativeDriver().openDatabase(":memory:")
        db.prepare("SELECT sqlite_version();").use { stmt ->
            assertEquals(true, stmt.step())
        }

        db.close()
    }
}
