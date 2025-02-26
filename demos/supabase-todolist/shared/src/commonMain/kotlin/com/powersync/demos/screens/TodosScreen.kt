package com.powersync.demos.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powersync.demos.NavController
import com.powersync.demos.Screen
import com.powersync.demos.components.Input
import com.powersync.demos.components.Menu
import com.powersync.demos.components.TodoList
import com.powersync.demos.components.WifiIcon
import com.powersync.demos.powersync.TodoItem

@Composable
internal fun TodosScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    items: List<TodoItem>,
    inputText: String,
    isConnected: Boolean,
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
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp)
                ) },
            navigationIcon = {
                IconButton(onClick = { navController.navigate(Screen.Home) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                }
            },
            actions = {
                WifiIcon(isConnected)
                Spacer(modifier = Modifier.width(16.dp))
            }
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged,
            screen = Screen.Todos
        )

        Box(Modifier.weight(1F)) {
            TodoList(
                items = items,
                onItemClicked = onItemClicked,
                onItemDoneChanged = onItemDoneChanged,
                onItemDeleteClicked = onItemDeleteClicked
            )
        }
    }
}
