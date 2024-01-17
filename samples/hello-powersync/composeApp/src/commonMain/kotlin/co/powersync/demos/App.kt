package co.powersync.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.Divider
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
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
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier,
        topBar = {
            Box {
                TopAppBar(
                    title = { Text("Hello PowerSync") },
                )

                Divider(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                    color = Color.Black
                )
            }
        },
        content = { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding =
                    PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding() + 8.dp,
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding() + 80.dp
                    ),
                ) {
                    item {
                        content(text)
                    }
                }
            }
        },
        contentColor = Color.Unspecified,
    )
}

@Composable
fun content(text: String) {
    Text(text)
}
