package com.powersync.demos.powersync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

internal class Todo(
    private val db: PowerSyncDatabase,
    private val userId: String?
): ViewModel() {

    private val _inputText = MutableStateFlow<String>("")
    val inputText: StateFlow<String> = _inputText

    private val _editingItem = MutableStateFlow<TodoItem?>(null)
    val editingItem: StateFlow<TodoItem?> = _editingItem

    fun watchItems(listId: String?): Flow<List<TodoItem>> {
        return db.watch<TodoItem>("""
                SELECT * 
                FROM $TODOS_TABLE
                WHERE list_id = ?
                ORDER by id
            """,
        if(listId != null) listOf(listId) else null
        ) { cursor ->
            TodoItem(
                id = cursor.getString(0)!!,
                createdAt = cursor.getString(1),
                completedAt = cursor.getString(2),
                description = cursor.getString(3)!!,
                createdBy = cursor.getString(4),
                completedBy = cursor.getString(5),
                completed = cursor.getLong(6) == 1L,
                listId = cursor.getString(7)!!,
                photoId = cursor.getString(8)
            )
        }
    }

    fun onItemClicked(item: TodoItem) {
        _editingItem.value = item
    }

    fun onItemDoneChanged(item: TodoItem, isDone: Boolean) {
        updateItem(item = item) {
            it.copy(
                completed = isDone,
                completedBy = if(isDone) userId else null,
                completedAt = if(isDone) Clock.System.now().toString() else null
            )
        }
    }

    fun onItemDeleteClicked(item: TodoItem) {
        viewModelScope.launch {
            db.writeTransaction { tx ->
                tx.execute("DELETE FROM $TODOS_TABLE WHERE id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked(userId: String?, listId: String?) {
        if (_inputText.value.isBlank()) return

        if(userId == null || listId == null) {
            throw Exception("userId or listId is null")
        }

        viewModelScope.launch {
            db.writeTransaction { tx ->
                tx.execute(
                    "INSERT INTO $TODOS_TABLE (id, created_at, created_by, description, list_id) VALUES (uuid(), datetime(), ?, ?, ?)",
                    listOf(userId, _inputText.value, listId)
                )
            }
            _inputText.value = ""
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onEditorCloseClicked() {
        updateItem(item = requireNotNull(_editingItem.value)) { it.copy() }
        _editingItem.value = null
    }

    fun onEditorTextChanged(text: String) {
        updateEditingItem(item = requireNotNull(_editingItem.value)) {
            it.copy(description = text)
        }
    }

    fun onEditorDoneChanged(isDone: Boolean) {
        updateEditingItem(item = requireNotNull(_editingItem.value)) {
            it.copy(
                completed = isDone,
                completedBy = if(isDone) userId else null,
                completedAt = if(isDone) Clock.System.now().toString() else null
            )
        }
    }

    private fun updateEditingItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        _editingItem.value = transformer(item)
    }

    private fun updateItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        viewModelScope.launch {
            val updatedItem = transformer(item)
            Logger.i("Updating item: $updatedItem")
            db.writeTransaction { tx ->
                tx.execute(
                    "UPDATE $TODOS_TABLE SET description = ?, completed = ?, completed_by = ?, completed_at = ? WHERE id = ?",
                    listOf(updatedItem.description, updatedItem.completed, updatedItem.completedBy, updatedItem.completedAt, item.id)
                )
            }
        }
    }
}
