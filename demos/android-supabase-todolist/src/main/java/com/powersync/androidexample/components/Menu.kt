package com.powersync.demos.components

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Menu(
    isLoggedIn: Boolean,
    onSignOutSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (isLoggedIn) {
        TopAppBar(
            title = { Text("Your App") },
            navigationIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Sign Out") },
                onClick = {
                expanded = false
                onSignOutSelected()
            })
        }
    }
}

