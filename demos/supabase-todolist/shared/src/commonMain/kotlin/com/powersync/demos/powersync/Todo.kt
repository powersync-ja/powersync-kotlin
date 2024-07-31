package com.powersync.demos.powersync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

internal class Todo(
    private val db: PowerSyncDatabase,
    private val userId: String?
) {
    var state: TodoState by mutableStateOf(initialState())
        private set

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
        setState { copy(editingItem = item) }
    }

    fun onItemDoneChanged(item: TodoItem, isDone: Boolean) {
        updateItem(item = item) {
            it.copy(
                completed = isDone,
                completedBy = if(isDone) userId else null
            )
        }
    }

    fun onItemDeleteClicked(item: TodoItem) {
        runBlocking {
            db.writeTransaction {
                db.execute("DELETE FROM $TODOS_TABLE WHERE id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked(userId: String?, listId: String?) {
        if (state.inputText.isBlank()) return
        Logger.i("userId: $userId")
        if(userId == null || listId == null) {
            throw Exception("userId or listId is null")
        }

        runBlocking {
            db.writeTransaction {
                db.execute(
                    "INSERT INTO $TODOS_TABLE (id, created_at, created_by, description, list_id) VALUES (uuid(), datetime(), ?, ?, ?)",
                    listOf(userId, state.inputText, listId)
                )
            }
            setState {
                copy(inputText = "")
            }
        }
    }

    fun onInputTextChanged(text: String) {
        setState { copy(inputText = text) }
    }

    fun onEditorCloseClicked() {
        updateItem(item = requireNotNull(state.editingItem)) { it.copy() }
        setState { copy(editingItem = null) }
    }

    fun onEditorTextChanged(text: String) {
        updateEditingItem(item = requireNotNull(state.editingItem)) {
            it.copy(description = text)
        }
    }

    fun onEditorDoneChanged(isDone: Boolean) {
        updateEditingItem(item = requireNotNull(state.editingItem)) {
            it.copy(
                completed = isDone,
                completedBy = if(isDone) userId else null
            )
        }
    }

    private fun updateEditingItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        setState { copy(editingItem = transformer(item)) }
    }

    private fun updateItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        runBlocking {
            val updatedItem = transformer(item)
            db.writeTransaction {
                db.execute(
                    "UPDATE $TODOS_TABLE SET description = ?, completed = ? WHERE id = ?",
                    listOf(updatedItem.description, updatedItem.completed, item.id)
                )
            }
        }
    }


    private fun initialState(): TodoState =
        TodoState()

    private inline fun setState(update: TodoState.() -> TodoState) {
        state = state.update()
    }

    data class TodoState(
        val inputText: String = "",
        val editingItem: TodoItem? = null
    )
}
