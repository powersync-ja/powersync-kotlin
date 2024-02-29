package com.powersync.demos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncBuilder
import com.powersync.connectors.SupabaseConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

internal class RootStore(factory: DatabaseDriverFactory) {

    private val db = PowerSyncBuilder.from(factory, schema).build()
    private val connector = SupabaseConnector(
        powerSyncEndpoint = Config.POWERSYNC_URL,
        supabaseUrl = Config.SUPABASE_URL,
        supabaseKey = Config.SUPABASE_ANON_KEY
    )

    var state: RootState by mutableStateOf(initialState())
        private set

    init {
        runBlocking {
            try {
                connector.login(Config.SUPABASE_USER_EMAIL, Config.SUPABASE_USER_PASSWORD)
            } catch (e: Exception) {
                println("Could not connect to Supabase, have you configured an auth user and set `SUPABASE_USER_EMAIL` and `SUPABASE_USER_PASSWORD`?\n Error: $e")
            }
            db.connect(connector)
        }
    }

    fun watchItems(): Flow<List<TodoItem>> {
        return db.watch("SELECT * FROM todos") { cursor ->
            TodoItem(
                id = cursor.getString(0)!!,
                description = cursor.getString(1)!!,
                completed = cursor.getLong(2) == 1L
            )
        }
    }

    fun onItemClicked(item: TodoItem) {
        setState { copy(editingItem = item) }
    }

    fun onItemDoneChanged(item: TodoItem, isDone: Boolean) {
        updateItem(item = item) { it.copy(completed = isDone) }
    }

    fun onItemDeleteClicked(item: TodoItem) {
        runBlocking {
            db.writeTransaction {
                db.execute("DELETE FROM todos WHERE id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked() {
        if (state.inputText.isBlank()) return

        runBlocking {
            db.writeTransaction {
                db.execute(
                    "INSERT INTO todos (id, description, completed) VALUES (uuid(), ?, ?)",
                    listOf(state.inputText, 0L)
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
        updateEditingItem(item = requireNotNull(state.editingItem)) { it.copy(description = text) }
    }

    fun onEditorDoneChanged(isDone: Boolean) {
        updateEditingItem(item = requireNotNull(state.editingItem)) { it.copy(completed = isDone) }
    }

    private fun updateEditingItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        setState { copy(editingItem = transformer(item)) }
    }

    private fun updateItem(item: TodoItem, transformer: (item: TodoItem) -> TodoItem) {
        runBlocking {
            val updatedItem = transformer(item)
            db.writeTransaction {
                db.execute(
                    "UPDATE todos SET description = ?, completed = ? WHERE id = ?",
                    listOf(updatedItem.description, updatedItem.completed, item.id)
                )
            }
        }
    }


    private fun initialState(): RootState =
        RootState()

    private inline fun setState(update: RootState.() -> RootState) {
        state = state.update()
    }

    data class RootState(
        val inputText: String = "",
        val editingItem: TodoItem? = null
    )

}
