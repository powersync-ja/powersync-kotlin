package com.powersync.demos.powersync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.demos.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

internal class ListContent(
    private val db: PowerSyncDatabase,
    private val userId: String?
) {
    var state: ListContentState by mutableStateOf(initialState())
        private set

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId

    fun watchItems(): Flow<List<ListItem>> {
        return db.watch("""
            SELECT
                $LISTS_TABLE.*, COUNT($TODOS_TABLE.id) AS total_tasks, SUM(CASE WHEN $TODOS_TABLE.completed = true THEN 1 ELSE 0 END) as completed_tasks
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
        if (state.inputText.isBlank()) return

        runBlocking {
            db.writeTransaction {
                db.execute(
                    "INSERT INTO $LISTS_TABLE (id, created_at, name, owner_id) VALUES (uuid(), datetime(), ?, ?)",
                    listOf(state.inputText, userId)
                )
            }
            setState {
                copy(inputText = "")
            }
        }
    }

    fun onItemClicked(item: ListItem) {
        _selectedListId.value = item.id
    }

    fun onInputTextChanged(text: String) {
        setState { copy(inputText = text) }
    }

    private fun initialState(): ListContentState =
        ListContentState()

    private inline fun setState(update: ListContentState.() -> ListContentState) {
        state = state.update()
    }

    data class ListContentState(
        val inputText: String = ""
    )
}
