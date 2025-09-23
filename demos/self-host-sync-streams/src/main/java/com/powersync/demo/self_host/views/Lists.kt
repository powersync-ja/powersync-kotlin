package com.powersync.demo.self_host.views

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.powersync.compose.composeState
import com.powersync.demo.self_host.powersync.usePowerSync
import com.powersync.demo.self_host.powersync.useTodoDatabase
import com.powersync.integrations.sqldelight.EntrySummary
import com.powersync.integrations.sqldelight.Lists
import com.powersync.utils.JsonParam
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Lists(onItemClicked: (item: Lists) -> Unit) {
    val db = useTodoDatabase()
    val lists by db.todosQueries
        .allLists()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .collectAsState(emptyList())

    Box {
        val listState = rememberLazyListState()

        LazyColumn(state = listState) {
            items(lists) { item ->
                ListItem(
                    item = item,
                    onClicked = { onItemClicked(item) },
                )

                Divider()
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
        )
    }
}

@Composable
fun ListItem(
    item: Lists,
    onClicked: () -> Unit,
) {
    val db = usePowerSync()
    val stream = remember { db.syncStream("todos", mapOf("list" to JsonParam.String(item.id))) }
    val streamStatus =
        db.currentStatus
            .composeState()
            .value
            .forStream(stream)

    Column(modifier = Modifier.clickable(onClick = onClicked).padding(16.dp)) {
        Text(
            text = AnnotatedString(item.name),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (streamStatus == null) {
            Text(
                text = AnnotatedString("Entries in this list are not synced - click to subscribe!"),
            )
        } else if (!streamStatus.subscription.hasSynced) {
            CircularProgressIndicator(modifier = Modifier.size(8.dp))
        } else {
            val db = useTodoDatabase()
            val stats by db.todosQueries
                .entrySummary(item.id)
                .asFlow()
                .mapToOne(Dispatchers.IO)
                .collectAsState(EntrySummary(0, 0))

            Text(
                text = AnnotatedString("${stats.completed_entries} completed, ${stats.pending_entries} pending items"),
            )
        }
    }
}
