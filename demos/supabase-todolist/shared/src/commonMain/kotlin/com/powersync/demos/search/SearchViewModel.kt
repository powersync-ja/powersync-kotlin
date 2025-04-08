package com.powersync.demos.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.demos.powersync.LISTS_TABLE
import com.powersync.demos.powersync.ListItem
import com.powersync.demos.powersync.TODOS_TABLE
import com.powersync.demos.powersync.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val db: PowerSyncDatabase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(300) // Wait for 300ms of silence before triggering search
                .collectLatest { query -> // Use collectLatest to cancel previous searches if query changes quickly
                    executeSearch(query)
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun executeSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            _error.value = null
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Logger.d { "[SearchViewModel] Executing FTS search for: '$query'" }

            try {
                val results = withContext(Dispatchers.Default) {
                    searchFtsTables(query)
                }
                _searchResults.value = results
                Logger.d { "[SearchViewModel] Found ${results.size} results." }
            } catch (e: Exception) {
                Logger.e("Error during FTS search: ${e.message}", throwable = e)
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList() // Clear results on error
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- FTS Query Logic ---
    private suspend fun searchFtsTables(searchTerm: String): List<SearchResult> {
        val combinedResults = mutableListOf<SearchResult>()
        val ftsSearchTerm = "$searchTerm*" // Add wildcard for prefix matching

        db.readTransaction { tx: PowerSyncTransaction ->
            // 1. Search FTS tables to get IDs
            val listIds = tx.getAll(
                "SELECT id FROM fts_$LISTS_TABLE WHERE fts_$LISTS_TABLE MATCH ?",
                listOf(ftsSearchTerm),
            ) { cursor -> cursor.getString(0) as String }

            val todoIds = tx.getAll(
                "SELECT id FROM fts_$TODOS_TABLE WHERE fts_$TODOS_TABLE MATCH ?",
                listOf(ftsSearchTerm),
            ) { cursor -> cursor.getString(0) as String }

            // 2. Fetch full objects from main tables using the IDs (Handle empty ID lists)
            if (listIds.isNotEmpty()) {
                // Construct query like: SELECT * FROM lists WHERE id IN (?, ?, ...)
                val placeholders = listIds.joinToString(",") { "?" }
                val listItems = tx.getAll(
                    "SELECT * FROM $TODOS_TABLE WHERE id IN ($placeholders)", listIds
                ) { cursor -> ListItem.fromRow(cursor) }
                combinedResults.addAll(listItems.map { SearchResult.ListResult(it) })
            }
            if (todoIds.isNotEmpty()) {
                val placeholders = todoIds.joinToString(",") { "?" }
                val todoItems = tx.getAll(
                    "SELECT * FROM $TODOS_TABLE WHERE id IN ($placeholders)",
                    todoIds
                ) { cursor -> TodoItem.fromRow(cursor) }
                combinedResults.addAll(todoItems.map { SearchResult.TodoResult(it) })
            }
        }

        return combinedResults
    }
}