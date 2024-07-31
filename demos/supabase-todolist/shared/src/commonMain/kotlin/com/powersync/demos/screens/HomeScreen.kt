package com.powersync.demos.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.demos.Screen
import com.powersync.demos.components.Input
import com.powersync.demos.components.ListContent
import com.powersync.demos.components.Menu
import com.powersync.demos.powersync.ListItem

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    items: List<ListItem>,
    inputText: String,
    isLoggedIn: Boolean,
//    onSqlConsoleSelected: () -> Unit,
    onSignOutSelected: () -> Unit,
    onItemClicked: (item: ListItem) -> Unit,
    onItemDeleteClicked: (item: ListItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = { Text(text = "Todo Lists") },
            navigationIcon = if (isLoggedIn) {
                {
                    Menu(
                        isLoggedIn,
//                        onSqlConsoleSelected,
                        onSignOutSelected
                    )
                }
            } else null
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged,
            screen = Screen.Home
        )

        Box(Modifier.weight(1F)) {
            ListContent(
                items = items,
                onItemClicked = onItemClicked,
                onItemDeleteClicked = onItemDeleteClicked
            )
        }
    }
}