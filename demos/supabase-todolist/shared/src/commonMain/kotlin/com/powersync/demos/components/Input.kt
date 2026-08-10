package com.powersync.demos.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.powersync.demos.Screen
import com.powersync.demos.onKeyUp
import org.jetbrains.compose.resources.painterResource
import powersync_root.demos.supabase_todolist.shared.generated.resources.Res
import powersync_root.demos.supabase_todolist.shared.generated.resources.add

@Composable
internal fun Input(
    text: String,
    onTextChanged: (String) -> Unit,
    onAddClicked: () -> Unit,
    screen: Screen,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        OutlinedTextField(
            value = text,
            modifier = Modifier
                .weight(weight = 1F)
                .onKeyUp(key = Key.Enter, action = onAddClicked),
            onValueChange = onTextChanged,
            label = { Text(text = if(screen == Screen.Home) "Add a list" else "Add a todo") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onAddClicked) {
            Icon(
                painter = painterResource(Res.drawable.add),
                contentDescription = null
            )
        }
    }
}
