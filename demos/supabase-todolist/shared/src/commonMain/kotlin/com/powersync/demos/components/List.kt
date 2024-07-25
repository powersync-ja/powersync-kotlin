package com.powersync.demos.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.powersync.demos.powersync.TodoItem
import com.powersync.demos.VerticalScrollbar
import com.powersync.demos.rememberScrollbarAdapter

@Composable
internal fun ListContent(
    items: List<TodoItem>,
    onItemClicked: (id: TodoItem) -> Unit,
    onItemDoneChanged: (item: TodoItem, isDone: Boolean) -> Unit,
    onItemDeleteClicked: (item: TodoItem) -> Unit,
) {
    Box {
        val listState = rememberLazyListState()

        LazyColumn(state = listState) {
            items(items) { item ->
                ListItem(
                    item = item,
                    onClicked = { onItemClicked(item) },
                    onDoneChanged = { onItemDoneChanged(item, it) },
                    onDeleteClicked = { onItemDeleteClicked(item) }
                )

                Divider()
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState)
        )
    }
}