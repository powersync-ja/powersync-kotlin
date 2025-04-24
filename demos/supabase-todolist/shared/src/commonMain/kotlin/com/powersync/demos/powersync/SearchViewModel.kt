package com.powersync.demos.powersync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.db.internal.PowerSyncTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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

    private val _selectedSearchResult = MutableStateFlow<SearchResult?>(null)
    val selectedSearchResult: StateFlow<SearchResult?> = _selectedSearchResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collectLatest { query ->
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
            Logger.Companion.d { "[SearchViewModel] Executing FTS search for: '$query'" }

            try {
                val results = withContext(Dispatchers.Default) {
                    searchFtsTables(query)
                }
                _searchResults.value = results
                Logger.Companion.d { "[SearchViewModel] Found ${results.size} results." }
            } catch (e: Exception) {
                Logger.Companion.e("Error during FTS search: ${e.message}", throwable = e)
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList() // Clear results on error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchResultClicked(result: SearchResult) {
        _selectedSearchResult.value = result
    }

    fun clearState() {
        Logger.d { "[SearchViewModel] Clearing state." }
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _selectedSearchResult.value = null
        _isLoading.value = false
        _error.value = null
    }

    private suspend fun searchFtsTables(searchTerm: String): List<SearchResult> {
        val combinedResults = mutableListOf<SearchResult>()
        val ftsSearchTerm = "$searchTerm*"

        db.readTransaction { tx: PowerSyncTransaction ->
            // 1. Search FTS tables to get IDs
            val listIds = tx.getAll(
                "SELECT id FROM fts_$LISTS_TABLE WHERE fts_$LISTS_TABLE MATCH ?",
                listOf(ftsSearchTerm),
            ) { cursor -> cursor.getString(0) as String }
            Logger.Companion.d { "[SearchViewModel] Found ${listIds.size} listIds." }
            val todoIds = tx.getAll(
                "SELECT id FROM fts_$TODOS_TABLE WHERE fts_$TODOS_TABLE MATCH ?",
                listOf(ftsSearchTerm),
            ) { cursor -> cursor.getString(0) as String }
            Logger.Companion.d { "[SearchViewModel] Found ${todoIds.size} todoIds." }
            // 2. Fetch full objects from main tables using the IDs (Handle empty ID lists)
            if (listIds.isNotEmpty()) {
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