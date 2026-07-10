import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema
import com.powersync.demos.App

@Composable fun MainView() = App(::openPowerSyncDatabase, Modifier.fillMaxSize())

private fun openPowerSyncDatabase(schema: Schema): PowerSyncDatabase {
    return PowerSyncDatabase(DatabaseDriverFactory(), schema)
}
