package co.powersync.demos


import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import co.powersync.db.DatabaseDriverFactory
import co.powersync.Greeting
import co.powersync.db.PowerSyncDatabase
import co.powersync.db.PowerSyncDatabaseConfig
import co.powersync.db.schema.Schema
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
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
                        val db = driverFactory?.let { PowerSyncDatabase( config =
                            object : PowerSyncDatabaseConfig {
                                override val driverFactory: DatabaseDriverFactory = it
                                override val schema: Schema
                                    get() = TODO("Not yet implemented")
                                override val dbFilename: String
                                    get() = "powersync.db"
                            }
                        )  }
                         Greeting().greet() + " PowerSync version: " + db?.getPowersyncVersion()
                    } catch (e: Exception) {
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