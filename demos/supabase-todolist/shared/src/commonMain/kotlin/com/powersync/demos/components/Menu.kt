package com.powersync.demos.components

import androidx.compose.material.*
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.painterResource
import powersync_root.demos.supabase_todolist.shared.generated.resources.Res
import powersync_root.demos.supabase_todolist.shared.generated.resources.menu

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
                    Icon(painter = painterResource(Res.drawable.menu), contentDescription = "Menu")
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(onClick = {
                expanded = false
                onSignOutSelected()
            }) {
                Text("Sign Out")
            }
        }
    }
}

