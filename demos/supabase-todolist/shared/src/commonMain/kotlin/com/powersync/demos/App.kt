package com.powersync.demos

import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.powersync.PowerSyncDatabase
import com.powersync.compose.composeState
import com.powersync.connector.supabase.SupabaseAuthenticator
import com.powersync.connector.supabase.SupabaseUploader
import com.powersync.db.schema.Schema
import com.powersync.demos.components.EditDialog
import com.powersync.demos.powersync.ListContent
import com.powersync.demos.powersync.ListItem
import com.powersync.demos.powersync.Todo
import com.powersync.demos.powersync.schema
import com.powersync.demos.screens.HomeScreen
import com.powersync.demos.screens.SignInScreen
import com.powersync.demos.screens.SignUpScreen
import com.powersync.demos.screens.TodosScreen
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.KoinApplication
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

val sharedAppModule = module {
    // This is overridden by the androidBackgroundSync example
    single { AuthOptions(connectFromViewModel = true) }
    single {
        createSupabaseClient(Config.SUPABASE_URL, Config.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }

    single { SupabaseAuthenticator(get()) }
    single { SupabaseUploader(get()) }

    single { NavController(Screen.Home) }
    viewModelOf(::AuthViewModel)
}

@Composable
fun App(
    openPowerSync: (Schema) -> PowerSyncDatabase,
    modifier: Modifier = Modifier,
) {
    fun KoinApplication.withDatabase() {
        modules(module { single { openPowerSync(schema) } }, sharedAppModule)
    }

    KoinApplication(koinConfiguration { withDatabase() }) {
        AppContent(modifier=modifier)
    }
}

@Composable
fun AppContent(
    db: PowerSyncDatabase = koinInject(),
    modifier: Modifier = Modifier,
) {

    val status by db.currentStatus.composeState()
    val coroutineScope = rememberCoroutineScope()

    val authViewModel = koinViewModel<AuthViewModel>()
    val navController = koinInject<NavController>()
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
        coroutineScope.launch {
            authViewModel.signOut()
        }
    }

    when (currentScreen) {
        is Screen.Home -> {
            if (authState == AuthState.SignedOut) {
                navController.navigate(Screen.SignIn)
            }

            val handleOnItemClicked = { item: ListItem ->
                lists.value.onItemClicked(item)
                navController.navigate(Screen.Todos)
            }

            HomeScreen(
                modifier = modifier.background(MaterialTheme.colors.background),
                items = items,
                onSignOutSelected = { handleSignOut() },
                inputText = listsInputText,
                onItemClicked = handleOnItemClicked,
                onItemDeleteClicked = lists.value::onItemDeleteClicked,
                onAddItemClicked = lists.value::onAddItemClicked,
                onInputTextChanged = lists.value::onInputTextChanged,
                syncStatus = status,
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
                syncStatus = status,
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
            if (authState == AuthState.SignedIn) {
                navController.navigate(Screen.Home)
            }

            SignInScreen(
                navController,
                authViewModel,
            )
        }

        is Screen.SignUp -> {
            if (authState == AuthState.SignedIn) {
                navController.navigate(Screen.Home)
            }

            SignUpScreen(
                navController,
                authViewModel,
            )
        }
    }
}
