package com.powersync.demos.search

import com.powersync.demos.powersync.ListItem
import com.powersync.demos.powersync.TodoItem

// Represents a unified search result item
sealed class SearchResult {
    data class ListResult(val item: ListItem) : SearchResult()
    data class TodoResult(val item: TodoItem) : SearchResult()
}