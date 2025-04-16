package com.powersync.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.powersync.PowerSyncDatabase
import com.powersync.androidexample.BuildConfig
import com.powersync.androidexample.ui.CameraService
import com.powersync.attachments.AttachmentQueue
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.compose.rememberDatabaseDriverFactory
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.connector.supabase.SupabaseRemoteStorage
import com.powersync.db.getString
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
fun App(
    cameraService: CameraService,
    attachmentDirectory: String,
) {
    val driverFactory = rememberDatabaseDriverFactory()
    val supabase =
        remember {
            SupabaseConnector(
                powerSyncEndpoint = BuildConfig.POWERSYNC_URL,
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
                storageBucket = BuildConfig.SUPABASE_ATTACHMENT_BUCKET,
            )
        }

    val db = remember { PowerSyncDatabase(driverFactory, schema) }
    val attachments =
        remember {
            if (BuildConfig.SUPABASE_ATTACHMENT_BUCKET != "null") {
                AttachmentQueue(
                    db = db,
                    remoteStorage = SupabaseRemoteStorage(supabase),
                    attachmentsDirectory = attachmentDirectory,
                    watchedAttachments =
                        db.watch(
                            "SELECT photo_id from todos WHERE photo_id IS NOT NULL",
                        ) {
                            WatchedAttachmentItem(
                                id = it.getString("photo_id"),
                                fileExtension = "jpg",
                            )
                        },
                )
            } else {
                null
            }
        }

    val syncStatus = db.currentStatus
    val status by syncStatus.asFlow().collectAsState(syncStatus)

    val navController =
        remember {
            NavController(Screen.Home)
        }

    val authViewModel =
        remember {
            AuthViewModel(supabase, db, navController, attachments)
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

    val todos = remember { mutableStateOf(Todo(db, attachments, userId)) }
    LaunchedEffect(currentUserId.value) {
        todos.value = Todo(db, attachments, currentUserId.value)
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
            if (authState == AuthState.SignedOut) {
                navController.navigate(Screen.SignIn)
            }

            val handleOnItemClicked = { item: ListItem ->
                lists.value.onItemClicked(item)
                navController.navigate(Screen.Todos)
            }

            HomeScreen(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                items = items,
                status = status,
                onSignOutSelected = { handleSignOut() },
                inputText = listsInputText,
                onItemClicked = handleOnItemClicked,
                onItemDeleteClicked = lists.value::onItemDeleteClicked,
                onAddItemClicked = lists.value::onAddItemClicked,
                onInputTextChanged = lists.value::onInputTextChanged,
            )
        }

        is Screen.Todos -> {
            val handleOnAddItemClicked = {
                todos.value.onAddItemClicked(userId, selectedListId)
            }

            TodosScreen(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                    onPhotoClear = todos.value::onPhotoDelete,
                    onPhotoCapture = { todos.value::onPhotoCapture.invoke(cameraService) },
                    attachmentsSupported = attachments != null,
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
