package com.powersync.demo.self_host.views

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.powersync.compose.composeSyncStream
import com.powersync.demo.self_host.powersync.usePowerSync
import com.powersync.demo.self_host.powersync.useTodoDatabase
import com.powersync.integrations.sqldelight.Lists
import com.powersync.integrations.sqldelight.Todos
import com.powersync.utils.JsonParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun Todos(
    list: Lists,
    onBack: () -> Unit,
) {
    val listId = list.id
    val db = usePowerSync()
    val stream = db.composeSyncStream("todos", mapOf("list" to JsonParam.String(listId)))

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
                title = { Text(list.name) },
            )
        },
        content = {
            if (stream?.subscription?.hasSynced != true) {
                LinearProgressIndicator()
                return@Scaffold
            }

            val items by useTodoDatabase()
                .todosQueries
                .allEntries(listId)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .collectAsState(emptyList())

            Box {
                val listState = rememberLazyListState()

                LazyColumn(state = listState) {
                    items(items) { item ->
                        TodoItem(item)
                        Divider()
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                )
            }
        },
    )
}

@Composable
fun TodoItem(item: Todos) {
    val db = useTodoDatabase()
    val scope = rememberCoroutineScope()

    Row {
        Spacer(modifier = Modifier.width(8.dp))

        Checkbox(
            checked = item.completed == 1L,
            modifier = Modifier.align(Alignment.CenterVertically),
            onCheckedChange = {
                scope.launch {
                    db.todosQueries.toggleEntry(
                        id = item.id,
                        completed =
                            if (item.completed == 0L) {
                                1L
                            } else {
                                0L
                            },
                    )
                }
            },
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = AnnotatedString(item.description),
            modifier = Modifier.weight(1F).align(Alignment.CenterVertically),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.width(8.dp))
    }
}
