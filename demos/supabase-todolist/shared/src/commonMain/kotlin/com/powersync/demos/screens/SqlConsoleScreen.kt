package com.powersync.demos.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powersync.PowerSyncDatabase
import com.powersync.demos.NavController
import com.powersync.demos.components.ResultSetTable
import com.powersync.demos.Screen
import com.powersync.demos.powersync.TodoItem

@Composable
internal fun SqlConsoleScreen(navController: NavController, db: PowerSyncDatabase) {
    var query by remember { mutableStateOf("SELECT * FROM todos") }
    val error by remember { mutableStateOf<String?>(null) }

    val data by db.watch(query) { cursor ->
        TodoItem(
            id = cursor.getString(0)!!,
            description = cursor.getString(1)!!,
            completed = cursor.getLong(2) == 1L
        )
    }.collectAsState(initial = emptyList())

    println(data)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("SQL Console") },
            navigationIcon = {
                IconButton(onClick = { navController.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("SQL Console", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("SQL Query") },
                enabled = false,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 4.dp))
            }

//            Button(
//                onClick = {
////                    executeQuery(driver, query)
////                        .catch { e ->
////                            error = e.message
////                        }
////                        .collect { result ->
////                            data = result
////                            error = null
////                        }
//                },
//                modifier = Modifier.align(Alignment.End).padding(vertical = 8.dp)
//            ) {
//                Text("Execute Query")
//            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Query Results:", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                ResultSetTable(
                    columns = listOf("id", "description", "completed"),
                    rows = data.map { item ->
                        listOf(
                        item.id,
                        item.description,
                        item.completed.toString()
                        )
                    }
                )
            }
        }
    }
}

