package com.powersync.demos

import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory

@Composable
fun RootContent(factory: DatabaseDriverFactory, modifier: Modifier = Modifier) {
    val model = remember { RootStore(factory) }
    val state = model.state
    val items by model.watchItems().collectAsState(initial = emptyList())

    MainContent(
        modifier = modifier.background(MaterialTheme.colors.background),
        items = items,
        inputText = state.inputText,
        onItemClicked = model::onItemClicked,
        onItemDoneChanged = model::onItemDoneChanged,
        onItemDeleteClicked = model::onItemDeleteClicked,
        onAddItemClicked = model::onAddItemClicked,
        onInputTextChanged = model::onInputTextChanged,
    )


//    if (state.editingItem != null) {
//        EditDialog(
//            item = state.editingItem,
//            onCloseClicked = model::onEditorCloseClicked,
//            onTextChanged = model::onEditorTextChanged,
//            onDoneChanged = model::onEditorDoneChanged,
//        )
//    }
}
