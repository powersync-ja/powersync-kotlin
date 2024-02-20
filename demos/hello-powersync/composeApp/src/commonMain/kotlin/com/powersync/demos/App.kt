package com.powersync.demos

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material.primarySurface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun App(powerSync: PowerSync) {
    var version by remember { mutableStateOf("Loading") }
    val scope = rememberCoroutineScope()
    val users by powerSync.watchUsers().collectAsState(emptyList())

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            LaunchedEffect(powerSync) {
                scope.launch {
                    version = """PowerSync version: ${powerSync.getPowersyncVersion()}"""
                }
            }

            ViewContent(version,
                users = users,
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
                })
        }
    }
}

@Composable
fun ViewContent(version: String, users: List<User>, onCreate: () -> Unit, onDelete: () -> Unit) {
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
                        bottom = padding.calculateBottomPadding() + 80.dp
                    ),
                ) {
                    item {

                        ListItem(
                            "Name",
                            "Email",
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp), divider = false
                        )
                    }
                    items(users) {
                        ListItem(it.name, it.email)
                    }

                    item {
                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column {
                                MyButton(label = "Create User") {
                                    onCreate()
                                }
                            }
                            Column {
                                MyButton(label = "Delete User") {
                                    onDelete()
                                }
                            }
                        }

                    }
                }
                // This box should be at the bottom of the screen
                Box(modifier = Modifier.padding(24.dp).align(Alignment.BottomEnd)) {
                    Text(version)
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
                            style = style ?: LocalTextStyle.current
                        )
                    }
                }
            }

            if (divider) {
                Divider(
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.BottomStart)
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
            .clickable(onClick = onClick).border(
                width = 1.dp,
                color = MaterialTheme.colors.primarySurface
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.primarySurface,
        )
    }
}
