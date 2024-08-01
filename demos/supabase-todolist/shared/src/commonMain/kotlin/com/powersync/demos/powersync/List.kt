package com.powersync.demos.powersync

import androidx.lifecycle.ViewModel
import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

internal class ListContent(
    private val db: PowerSyncDatabase,
    private val userId: String?
): ViewModel() {
    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId

    private val _inputText = MutableStateFlow<String>("")
    val inputText: StateFlow<String> = _inputText

    fun watchItems(): Flow<List<ListItem>> {
        return db.watch("""
            SELECT
                *
            FROM
                $LISTS_TABLE
            LEFT JOIN $TODOS_TABLE
            ON  $LISTS_TABLE.id = $TODOS_TABLE.list_id
            GROUP BY $LISTS_TABLE.id
        """) { cursor ->
            ListItem(
                id = cursor.getString(0)!!,
                createdAt = cursor.getString(1)!!,
                name = cursor.getString(2)!!,
                ownerId = cursor.getString(3)!!
            )
        }
    }

    fun onItemDeleteClicked(item: ListItem) {
        runBlocking {
            db.writeTransaction {
                db.execute("DELETE FROM $LISTS_TABLE WHERE id = ?", listOf(item.id))
                db.execute("DELETE FROM $TODOS_TABLE WHERE list_id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked() {
        if (_inputText.value.isBlank()) return

        runBlocking {
            db.writeTransaction {
                db.execute(
                    "INSERT INTO $LISTS_TABLE (id, created_at, name, owner_id) VALUES (uuid(), datetime(), ?, ?)",
                    listOf(_inputText.value, userId)
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
