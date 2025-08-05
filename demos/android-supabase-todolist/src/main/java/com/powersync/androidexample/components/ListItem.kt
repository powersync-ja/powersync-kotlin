package com.powersync.demos.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.powersync.demos.MARGIN_SCROLLBAR
import com.powersync.demos.powersync.ListItem

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
                imageVector = Icons.Default.Delete,
                contentDescription = null
            )
        }

        Spacer(modifier = Modifier.width(MARGIN_SCROLLBAR))
    }
}
