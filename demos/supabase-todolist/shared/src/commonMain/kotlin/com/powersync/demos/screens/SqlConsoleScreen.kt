package com.powersync.demos.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    val listOfQueryItems = mutableListOf<Map<String, String>>()

    val data by db.watch(query) { cursor ->
        run {
            val map = mutableMapOf<String, String>()
            val count = mutableStateOf(0)
            for (i in 0..10) {
                if(cursor.getString(i) != null) {
                    map.put(count.value.toString(), cursor.getString(count.value)!!)
                    count.value++
                } else {
                    listOfQueryItems.add(map.toMap())
                    map.clear()
                    count.value = 0
                    break
                }
                print(listOfQueryItems)
            }
        }
    }.collectAsState(initial = emptyList())

    println("HELLO")
    println(data)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SQL Console",
                        textAlign = TextAlign.Center
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigate(Screen.Home) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

//            Box(modifier = Modifier.weight(1f)) {
//                ResultSetTable(
//                    columns = (0..count).map { it.toString() },
//                    rows = data.map { item ->
//                        val list = mutableListOf<String>()
//                        for (i in 0..count) {
//                            list.add(map[i.toString()]!!)
//                        }
//                        list
//                    }
//                )
//            }
        }
    }
}
