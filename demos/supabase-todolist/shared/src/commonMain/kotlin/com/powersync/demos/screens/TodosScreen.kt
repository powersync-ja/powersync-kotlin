package com.powersync.demos.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powersync.PowerSyncDatabase
import com.powersync.compose.composeSyncStream
import com.powersync.demos.NavController
import com.powersync.demos.Screen
import com.powersync.demos.components.Input
import com.powersync.demos.components.TodoList
import com.powersync.demos.components.WifiIcon
import com.powersync.demos.powersync.TodoItem
import com.powersync.sync.SyncStatusData
import com.powersync.utils.JsonParam
import org.koin.compose.koinInject

@Composable
internal fun TodosScreen(
    db: PowerSyncDatabase = koinInject(),
    modifier: Modifier = Modifier,
    navController: NavController,
    listId: String,
    items: List<TodoItem>,
    inputText: String,
    syncStatus: SyncStatusData,
    onItemClicked: (item: TodoItem) -> Unit,
    onItemDoneChanged: (item: TodoItem, isDone: Boolean) -> Unit,
    onItemDeleteClicked: (item: TodoItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    "Todo List",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigate(Screen.Home) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                }
            },
            actions = {
                WifiIcon(syncStatus)
                Spacer(modifier = Modifier.width(16.dp))
            },
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged,
            screen = Screen.Todos,
        )

        Box(Modifier.weight(1F)) {
            val stream = db.composeSyncStream("todos", mapOf("list" to JsonParam.String(listId)))

            if (stream?.subscription?.hasSynced != true) {
                val progress = stream?.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        progress = progress.fraction,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            } else {
                TodoList(
                    items = items,
                    onItemClicked = onItemClicked,
                    onItemDoneChanged = onItemDoneChanged,
                    onItemDeleteClicked = onItemDeleteClicked,
                )
            }
        }
    }
}
