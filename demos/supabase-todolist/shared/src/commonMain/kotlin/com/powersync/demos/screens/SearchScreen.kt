package com.powersync.demos.screens

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
import com.powersync.demos.Screen
import com.powersync.demos.components.SearchResultItem
import com.powersync.demos.powersync.SearchResult
import com.powersync.demos.powersync.SearchViewModel
import org.koin.compose.koinInject

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = koinInject(),
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
                    IconButton(onClick = {
                        viewModel.clearState()
                        navController.navigateBack()
                    }) {
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
                            items(searchResults, key = { result ->
                                when (result) {
                                    is SearchResult.ListResult -> "list_${result.item.id}"
                                    is SearchResult.TodoResult -> "todo_${result.item.id}"
                                }
                            }) { result ->
                                SearchResultItem(result) { clickedResult ->

                                    val listId = when (clickedResult) {
                                        is SearchResult.ListResult -> clickedResult.item.id
                                        is SearchResult.TodoResult -> clickedResult.item.listId
                                    }
                                    Logger.i { "Search item clicked, listId: $listId" }
                                    viewModel.onSearchResultClicked(clickedResult)
                                    navController.navigate(Screen.Todos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
