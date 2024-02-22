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
            connector.login(TEST_EMAIL, TEST_PASSWORD)
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

    fun onItemClicked(id: String) {
        setState { copy(editingItemId = id) }
    }

    fun onItemDoneChanged(id: String, isDone: Boolean) {
        updateItem(id = id) { it.copy(completed = isDone) }
    }

    fun onItemDeleteClicked(id: String) {
        runBlocking {
            db.execute("DELETE FROM todos WHERE id = ?", listOf(id))
        }
    }

    fun onAddItemClicked() {

        runBlocking {
            db.execute(
                "INSERT INTO todos (id, text, isDone) VALUES (uuid(), ?, ?)",
                listOf(state.inputText, 0L)
            )
        }
    }

    fun onInputTextChanged(text: String) {
        setState { copy(inputText = text) }
    }

    fun onEditorCloseClicked() {
        setState { copy(editingItemId = null) }
    }

    fun onEditorTextChanged(text: String) {
        updateItem(id = requireNotNull(state.editingItemId)) { it.copy(description = text) }
    }

    fun onEditorDoneChanged(isDone: Boolean) {
        updateItem(id = requireNotNull(state.editingItemId)) { it.copy(completed = isDone) }
    }

    private fun updateItem(id: String, transformer: (item: TodoItem) -> TodoItem) {
        runBlocking {
            val item = db.getOptional("SELECT * FROM todos WHERE id = ?", listOf(id)) {
                TodoItem(
                    id = it.getString(0)!!,
                    description = it.getString(1)!!,
                    completed = it.getLong(2) == 1L
                )
            } ?: return@runBlocking

            val updatedItem = transformer(item)
            db.execute(
                "UPDATE todos SET description = ?, completed = ? WHERE id = ?",
                listOf(id, updatedItem.description, updatedItem.completed)
            )
        }
    }


    private fun initialState(): RootState =
        RootState()

    private inline fun setState(update: RootState.() -> RootState) {
        state = state.update()
    }

    data class RootState(
        val inputText: String = "",
        val editingItemId: String? = null,
    )

    companion object {
        private const val TEST_EMAIL = "hello@powersync.com"
        private const val TEST_PASSWORD = "@dYX0}72eS0kT=(YG@8("
    }
}
