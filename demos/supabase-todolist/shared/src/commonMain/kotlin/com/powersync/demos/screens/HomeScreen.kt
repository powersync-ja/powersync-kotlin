package com.powersync.demos.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.demos.components.Input
import com.powersync.demos.components.ListContent
import com.powersync.demos.components.Menu
import com.powersync.demos.powersync.TodoItem

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    items: List<TodoItem>,
    inputText: String,
    isLoggedIn: Boolean,
    onSqlConsoleSelected: () -> Unit,
    onSignOutSelected: () -> Unit,
    onItemClicked: (item: TodoItem) -> Unit,
    onItemDoneChanged: (item: TodoItem, isDone: Boolean) -> Unit,
    onItemDeleteClicked: (item: TodoItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = { Text(text = "Todo List") },
            navigationIcon = if (isLoggedIn) {
                {
                    Menu(
                        isLoggedIn,
                        onSqlConsoleSelected,
                        onSignOutSelected
                    )
                }
            } else null
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged
        )

        Box(Modifier.weight(1F)) {
            ListContent(
                items = items,
                onItemClicked = onItemClicked,
                onItemDoneChanged = onItemDoneChanged,
                onItemDeleteClicked = onItemDeleteClicked
            )
        }
    }
}