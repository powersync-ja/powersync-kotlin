package com.powersync.demos.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.web.DatabaseImplementation
import com.powersync.web.WebConnectionFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        var database by remember { mutableStateOf<SQLiteConnectionPool?>(null) }
        var queryResults by remember { mutableStateOf("Query not running") }
        val composableScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .background(MaterialTheme.colors.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Has database: ${database != null}")
            Text(queryResults)

            fun openDatabase() {
                if (database == null) {
                    composableScope.launch {
                        val open = WebConnectionFactory()
                        val db = open.open("test.db", DatabaseImplementation.inMemoryShared)
                        database = db
                    }
                }
            }

            fun launchQuery() {
                composableScope.launch {
                    queryResults = "Running query..."
                    try {
                        database!!.read { context ->
                            context.usePreparedAsync("SELECT ?, ?, ?, ?") { stmt ->
                                stmt.bindNull(1)
                                stmt.bindText(2, "Hello from Kotlin")
                                stmt.bindInt(3, 123)
                                stmt.bindDouble(4, 1.23)

                                stmt.step()
                                queryResults = "Got row!"
                            }
                        }
                    } catch (e: Exception) {
                        queryResults = "Query error: $e"
                    }
                }
            }

            Button(onClick = ::openDatabase) {
                Text("Open database!")
            }
            Button(onClick = ::launchQuery) {
                Text("Launch query!")
            }
        }
    }
}