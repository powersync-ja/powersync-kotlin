package co.powersync.demos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.Divider
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material.primarySurface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            var users by remember { mutableStateOf(listOf<String>()) }
            var db by remember { mutableStateOf<PowerSyncDatabase?>(null) }
            LaunchedEffect(true) {

                scope.launch {
                    text = Greeting().greet();
                    try {
                        db = driverFactory?.let {
                            PowerSyncDatabase(config =
                            object : PowerSyncDatabaseConfig {
                                override val driverFactory: DatabaseDriverFactory = it
                                override val schema: Schema
                                    get() = Schema(
                                        listOf(
                                            Table(
                                                "users",
                                                listOf(
                                                    Column.text("name"),
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

                            db!!.connect(connector)

                            text += " PowerSync version: " + db!!.getPowersyncVersion()

                            users =
                                db!!.createQuery(
                                    "SELECT name, email FROM users",
                                    mapper = { cursor ->
                                        cursor.getString(0)!!
                                    }).executeAsList()
                        }

                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                        text = e.message ?: "error"
                    }
                }
            }
            GreetingView(text, users, db)
        }
    }
}

@Composable
fun GreetingView(text: String, users: List<String>, db: PowerSyncDatabase? = null) {
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier,
        topBar = {
            Box {
                TopAppBar(
                    title = { Text("Hello PowerSync") },
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
                        Box(modifier = Modifier.padding(24.dp)) {
                            Text(text)
                        }
                    }
                    items(users) {
                        ListItem(it)
                    }

                    item {
                        Spacer(Modifier.height(24.dp))

                        Column {
                            MyButton(label = "Create User") {
                                coroutineScope.launch {
                                    val result = db?.createQuery(
                                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                                        parameters = 2,
                                        binders = {
                                            bindString(0, "John")
                                            bindString(1, "john@example.com")
                                        })?.executeAsOneOrNull()
                                }
                            }
                        }
                    }
                }
            }

        },
        contentColor = Color.Unspecified,
    )
}

@Composable
private fun ListItem(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text
                        )
                    }
                }
            }
        }

        Divider(
            color = Color.Black,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}


@Composable
private fun MyButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.primarySurface
        )
    }
}