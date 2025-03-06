package com.powersync.demos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powersync.demos.Screen
import com.powersync.demos.components.Input
import com.powersync.demos.components.ListContent
import com.powersync.demos.components.Menu
import com.powersync.demos.components.WifiIcon
import com.powersync.demos.powersync.ListItem
import com.powersync.sync.SyncStatusData

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    items: List<ListItem>,
    inputText: String,
    syncStatus: SyncStatusData,
    onSignOutSelected: () -> Unit,
    onItemClicked: (item: ListItem) -> Unit,
    onItemDeleteClicked: (item: ListItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    "Todo Lists",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
            },
            navigationIcon = {
                Menu(
                    true,
                    onSignOutSelected,
                )
            },
            actions = {
                WifiIcon(syncStatus)
                Spacer(modifier = Modifier.width(16.dp))
            },
        )

        when {
            syncStatus.hasSynced == null || syncStatus.hasSynced == false -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Busy with initial sync...",
                        style = MaterialTheme.typography.h6,
                    )
                }
            }

            else -> {
                Input(
                    text = inputText,
                    onAddClicked = onAddItemClicked,
                    onTextChanged = onInputTextChanged,
                    screen = Screen.Home,
                )

                Box(Modifier.weight(1F)) {
                    ListContent(
                        items = items,
                        onItemClicked = onItemClicked,
                        onItemDeleteClicked = onItemDeleteClicked,
                    )
                }
            }
        }
    }
}
