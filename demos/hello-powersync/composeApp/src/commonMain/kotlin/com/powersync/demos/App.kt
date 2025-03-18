package com.powersync.demos

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powersync.compose.rememberDatabaseDriverFactory
import com.powersync.sync.SyncStatusData
import kotlinx.coroutines.launch

@Composable
fun App() {
    val driverFactory = rememberDatabaseDriverFactory()
    val powerSync: PowerSync = remember { PowerSync(driverFactory) }

    var version by remember { mutableStateOf("Loading") }
    val scope = rememberCoroutineScope()
    val customers by powerSync.watchUsers().collectAsState(emptyList())
    val syncStatus by powerSync.db.currentStatus
        .asFlow()
        .collectAsState(powerSync.db.currentStatus)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            LaunchedEffect(powerSync) {
                scope.launch {
                    version =
                        """PowerSync version: ${powerSync.getPowersyncVersion()}"""
                }
            }

            ViewContent(
                version,
                users = customers,
                onCreate = {
                    scope.launch {
                        val person = generateRandomPerson()
                        powerSync.createUser(person.first, person.second)
                    }
                },
                onDelete = {
                    scope.launch {
                        powerSync.deleteUser()
                    }
                },
                syncStatus = syncStatus,
            )
        }
    }
}

@Composable
fun ViewContent(
    version: String,
    users: List<User>,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
    syncStatus: SyncStatusData,
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier,
        topBar = {
            Box {
                TopAppBar(
                    title = { Text("Hello PowerSync") },
                )
            }
        },
        content = { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = padding.calculateStartPadding(layoutDirection),
                            top = padding.calculateTopPadding() + 8.dp,
                            end = padding.calculateEndPadding(layoutDirection),
                            bottom = padding.calculateBottomPadding() + 80.dp,
                        ),
                ) {
                    item {
                        ListItem(
                            "Name",
                            "Email",
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp),
                            divider = false,
                        )
                    }
                    items(users) {
                        ListItem(it.name, it.email)
                    }

                    item {
                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Column {
                                MyButton(label = "Create") {
                                    onCreate()
                                }
                            }
                            Column {
                                MyButton(label = "Delete") {
                                    onDelete()
                                }
                            }
                        }
                    }
                }
                // This box should be at the bottom of the screen
                Box(modifier = Modifier.padding(24.dp).align(Alignment.BottomEnd)) {
                    Column {
                        Text(version)
                        Text("""Connected: ${syncStatus.connected}""")
                        Text("""Connecting: ${syncStatus.connecting}""")
                    }
                }
            }
        },
        contentColor = Color.Unspecified,
    )
}

@Composable
fun ListItem(
    vararg cols: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    divider: Boolean = true,
) {
    Box(modifier = modifier) {
        Box {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    cols.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            style = style ?: LocalTextStyle.current,
                        )
                    }
                }
            }

            if (divider) {
                Divider(
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

@Composable
fun MyButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colors.primarySurface,
                ).padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.primarySurface,
        )
    }
}
