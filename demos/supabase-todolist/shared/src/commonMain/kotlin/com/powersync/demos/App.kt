package com.powersync.demos

import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.bucket.BucketPriority
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.demos.components.EditDialog
import com.powersync.demos.powersync.ListContent
import com.powersync.demos.powersync.ListItem
import com.powersync.demos.powersync.Todo
import com.powersync.demos.powersync.schema
import com.powersync.demos.screens.HomeScreen
import com.powersync.demos.screens.SignInScreen
import com.powersync.demos.screens.SignUpScreen
import com.powersync.demos.screens.TodosScreen
import kotlinx.coroutines.runBlocking


@Composable
fun App(factory: DatabaseDriverFactory, modifier: Modifier = Modifier) {
    val supabase = remember {
        SupabaseConnector(
            powerSyncEndpoint = Config.POWERSYNC_URL,
            supabaseUrl = Config.SUPABASE_URL,
            supabaseKey = Config.SUPABASE_ANON_KEY
        )
    }
    val db = remember { PowerSyncDatabase(factory, schema) }
    val status by db.currentStatus.asFlow().collectAsState(initial = db.currentStatus)

    // This assumes that the buckets for lists has a priority of 1 (but it will work fine with sync
    // rules not defining any priorities at all too). When giving lists a higher priority than
    // items, we can have a consistent snapshot of lists without items. In the case where many items
    // exist (that might take longer to sync initially), this allows us to display lists earlier.
    val hasSyncedLists by remember {
        derivedStateOf { status.statusForPriority(BucketPriority(1)).hasSynced }
    }

    val navController = remember { NavController(Screen.Home) }
    val authViewModel = remember {
        AuthViewModel(supabase, db, navController)
    }

    val authState by authViewModel.authState.collectAsState()
    val currentScreen by navController.currentScreen.collectAsState()

    val userId by authViewModel.userId.collectAsState()
    val currentUserId = rememberUpdatedState(userId)
    val lists = remember { mutableStateOf(ListContent(db, userId)) }
    LaunchedEffect(currentUserId.value) {
        lists.value = ListContent(db, currentUserId.value)
    }
    val selectedListId by lists.value.selectedListId.collectAsState()
    val items by lists.value.watchItems().collectAsState(initial = emptyList())
    val listsInputText by lists.value.inputText.collectAsState()

    val todos = remember { mutableStateOf(Todo(db, userId)) }
    LaunchedEffect(currentUserId.value) {
        todos.value = Todo(db, currentUserId.value)
    }
    val todoItems by todos.value.watchItems(selectedListId).collectAsState(initial = emptyList())
    val editingItem by todos.value.editingItem.collectAsState()
    val todosInputText by todos.value.inputText.collectAsState()

    fun handleSignOut() {
        runBlocking {
            authViewModel.signOut()
        }
    }

    when (currentScreen) {
        is Screen.Home -> {
            if(authState == AuthState.SignedOut) {
                navController.navigate(Screen.SignIn)
            }

            val handleOnItemClicked = { item: ListItem ->
                lists.value.onItemClicked(item)
                navController.navigate(Screen.Todos)
            }

            HomeScreen(
                modifier = modifier.background(MaterialTheme.colors.background),
                items = items,
                isConnected = status.connected,
                onSignOutSelected = { handleSignOut() },
                inputText = listsInputText,
                onItemClicked = handleOnItemClicked,
                onItemDeleteClicked = lists.value::onItemDeleteClicked,
                onAddItemClicked = lists.value::onAddItemClicked,
                onInputTextChanged = lists.value::onInputTextChanged,
                hasSynced = hasSyncedLists
            )
        }

        is Screen.Todos -> {
            val handleOnAddItemClicked = {
                todos.value.onAddItemClicked(userId, selectedListId)
            }

            TodosScreen(
                modifier = modifier.background(MaterialTheme.colors.background),
                navController = navController,
                items = todoItems,
                isConnected = status.connected,
                inputText = todosInputText,
                onItemClicked = todos.value::onItemClicked,
                onItemDoneChanged = todos.value::onItemDoneChanged,
                onItemDeleteClicked = todos.value::onItemDeleteClicked,
                onAddItemClicked = handleOnAddItemClicked,
                onInputTextChanged = todos.value::onInputTextChanged,
            )

            editingItem?.also {
                EditDialog(
                    item = it,
                    onCloseClicked = todos.value::onEditorCloseClicked,
                    onTextChanged = todos.value::onEditorTextChanged,
                    onDoneChanged = todos.value::onEditorDoneChanged,
                )
            }
        }

        is Screen.SignIn -> {
            if(authState == AuthState.SignedIn) {
                navController.navigate(Screen.Home)
            }

            SignInScreen(
                navController,
                authViewModel
            )
        }

        is Screen.SignUp -> {
            if(authState == AuthState.SignedIn) {
                navController.navigate(Screen.Home)
            }

            SignUpScreen(
                navController,
                authViewModel
            )
        }
    }
}
