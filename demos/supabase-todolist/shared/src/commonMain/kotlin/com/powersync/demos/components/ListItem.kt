package com.powersync.demos.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.powersync.demos.MARGIN_SCROLLBAR
import com.powersync.demos.powersync.ListItem
import org.jetbrains.compose.resources.painterResource
import powersync_root.demos.supabase_todolist.shared.generated.resources.Res
import powersync_root.demos.supabase_todolist.shared.generated.resources.delete

@Composable
internal fun ListItem(
    item: ListItem,
    onClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    Row(modifier = Modifier.clickable(onClick = onClicked)) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = AnnotatedString(item.name),
            modifier = Modifier.weight(1F).align(Alignment.CenterVertically),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onDeleteClicked) {
            Icon(
                painter = painterResource(Res.drawable.delete),
                contentDescription = null
            )
        }

        Spacer(modifier = Modifier.width(MARGIN_SCROLLBAR))
    }
}
