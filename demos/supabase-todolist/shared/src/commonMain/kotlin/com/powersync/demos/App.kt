package com.powersync.demos

import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.demos.components.EditDialog
import com.powersync.demos.powersync.Todo
import com.powersync.demos.powersync.schema
import com.powersync.demos.screens.HomeScreen
import com.powersync.demos.screens.SignInScreen
import com.powersync.demos.screens.SignUpScreen
import com.powersync.demos.screens.SqlConsoleScreen
import kotlinx.coroutines.runBlocking

@Composable
fun App(factory: DatabaseDriverFactory, modifier: Modifier = Modifier) {
    val db = remember { PowerSyncDatabase(factory, schema) }
    val supabase = remember {
        SupabaseConnector(
            powerSyncEndpoint = Config.POWERSYNC_URL,
            supabaseUrl = Config.SUPABASE_URL,
            supabaseKey = Config.SUPABASE_ANON_KEY
        )
    }

    val todos = remember { Todo(db) }
    val navController = remember { NavController(Screen.SignIn) }
    val authViewModel = remember { AuthViewModel(supabase, db) }
    val authState by authViewModel.authState.collectAsState()
    val items by todos.watchItems().collectAsState(initial = emptyList())
    val currentScreen by navController.currentScreen.collectAsState()

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

            HomeScreen(
                modifier = modifier.background(MaterialTheme.colors.background),
                items = items,
                isLoggedIn = true,
                onSqlConsoleSelected = { navController.navigate(Screen.SqlConsole) },
                onSignOutSelected = { handleSignOut() },
                inputText = todos.state.inputText,
                onItemClicked = todos::onItemClicked,
                onItemDoneChanged = todos::onItemDoneChanged,
                onItemDeleteClicked = todos::onItemDeleteClicked,
                onAddItemClicked = todos::onAddItemClicked,
                onInputTextChanged = todos::onInputTextChanged,
            )

            todos.state.editingItem?.also {
                EditDialog(
                    item = it,
                    onCloseClicked = todos::onEditorCloseClicked,
                    onTextChanged = todos::onEditorTextChanged,
                    onDoneChanged = todos::onEditorDoneChanged,
                )
            }
        }
        is Screen.SqlConsole -> {
            SqlConsoleScreen(navController, db)
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