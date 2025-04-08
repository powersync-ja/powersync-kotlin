package com.powersync.demos.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.powersync.demos.NavController
import org.koin.compose.koinInject // Use koinInject or specific platform injection

@Composable
fun SearchScreen(
    navController: NavController, // Inject or pass NavController
    viewModel: SearchViewModel = koinInject() // Inject the ViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Lists & Todos") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateBack() }) { // Or specific back navigation
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- Search Input Field ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Search...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Results Area ---
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    error != null -> {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    searchResults.isEmpty() && searchQuery.isNotEmpty() && !isLoading -> {
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    searchResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { result -> // Provide stable keys
                                when (result) {
                                    is SearchResult.ListResult -> "list_${result.item.id}"
                                    is SearchResult.TodoResult -> "todo_${result.item.id}"
                                }
                            }) { result ->
                                SearchResultItem(result) { clickedResult ->
                                    // --- Handle Click ---
                                    // Example: Navigate to the list containing the todo or the list itself
                                    val listId = when (clickedResult) {
                                        is SearchResult.ListResult -> clickedResult.item.id
                                        is SearchResult.TodoResult -> clickedResult.item.listId
                                    }
                                    Logger.i { "Search item clicked, listId: $listId" }
                                    // navController.navigateTo(Screen.TodoList(listId)) // Adapt to your navigation
                                }
                            }
                        }
                    }
                    // Implicit else: Initial state (empty query, no results, not loading) - show nothing or a prompt
                }
            }
        }
    }
}

// --- Composable for a single search result item ---
@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: (SearchResult) -> Unit
) {
    // Basic card implementation - customize as needed
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(result) },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Example Icon - could be different per type
            // Icon(...)
            // Spacer(modifier = Modifier.width(8.dp))
            Column {
                when (result) {
                    is SearchResult.ListResult -> {
                        Text(result.item.name, style = MaterialTheme.typography.h6)
                        Text("List", style = MaterialTheme.typography.caption)
                    }
                    is SearchResult.TodoResult -> {
                        Text(result.item.description, style = MaterialTheme.typography.body1)
                        Text("Todo Item", style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
    }
}