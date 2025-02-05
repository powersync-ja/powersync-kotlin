package com.powersync.demos.powersync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.PowerSyncDatabase
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class ListContent(
    private val db: PowerSyncDatabase,
    private val userId: String?,
) : ViewModel() {
    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId

    private val _inputText = MutableStateFlow<String>("")
    val inputText: StateFlow<String> = _inputText

    fun watchItems(): Flow<List<ListItem>> =
        db.watch(
            """
            SELECT
                *
            FROM
                $LISTS_TABLE
            LEFT JOIN $TODOS_TABLE
            ON  $LISTS_TABLE.id = $TODOS_TABLE.list_id
            GROUP BY $LISTS_TABLE.id
        """,
        ) { cursor ->
            ListItem(
                id = cursor.getString("id"),
                createdAt = cursor.getString("created_at"),
                name = cursor.getString("name"),
                ownerId = cursor.getString("owner_id"),
            )
        }

    fun onItemDeleteClicked(item: ListItem) {
        viewModelScope.launch {
            db.writeTransaction { tx ->
                tx.execute("DELETE FROM $LISTS_TABLE WHERE id = ?", listOf(item.id))
                tx.execute("DELETE FROM $TODOS_TABLE WHERE list_id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked() {
        if (_inputText.value.isBlank()) return

        viewModelScope.launch {
            db.writeTransaction { tx ->
                tx.execute(
                    "INSERT INTO $LISTS_TABLE (id, created_at, name, owner_id) VALUES (uuid(), datetime(), ?, ?)",
                    listOf(_inputText.value, userId),
                )
            }
            _inputText.value = ""
        }
    }

    fun onItemClicked(item: ListItem) {
        _selectedListId.value = item.id
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }
}
