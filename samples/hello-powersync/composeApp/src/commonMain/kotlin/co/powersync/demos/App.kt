package co.powersync.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import co.powersync.db.DatabaseDriverFactory
import co.powersync.Greeting
import co.powersync.connectors.SupabaseConnector
import co.powersync.db.PowerSyncDatabase
import co.powersync.db.PowerSyncDatabaseConfig
import co.powersync.db.schema.Column
import co.powersync.db.schema.Schema
import co.powersync.db.schema.Table
import kotlinx.coroutines.launch

@Composable
fun App(driverFactory: DatabaseDriverFactory?) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            val scope = rememberCoroutineScope()
            var text by remember { mutableStateOf("Loading") }
            LaunchedEffect(true) {
                scope.launch {
                    text = try {
                        val db = driverFactory?.let {
                            PowerSyncDatabase(config =
                            object : PowerSyncDatabaseConfig {
                                override val driverFactory: DatabaseDriverFactory = it
                                override val schema: Schema
                                    get() = Schema(
                                        listOf(
                                            Table(
                                                "users",
                                                listOf(
                                                    Column.text("username"),
                                                    Column.text("email")
                                                )
                                            )
                                        )
                                    )
                                override val dbFilename: String
                                    get() = "powersync.db"
                            }
                            )
                        }

                        if (db != null) {
                            val connector = SupabaseConnector()

                            db.connect(connector)
                        }

                        Greeting().greet() + " PowerSync version: " + db?.getPowersyncVersion()
                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                        e.message ?: "error"
                    }
                }
            }
            GreetingView(text)
        }
    }
}

@Composable
fun GreetingView(text: String) {
    Text(text = text)
}