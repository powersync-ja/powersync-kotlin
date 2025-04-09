package com.powersync.demos.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.powersync.demos.powersync.SearchResult

@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: (SearchResult) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick(result) },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                when (result) {
                    is SearchResult.ListResult -> {
                        Text(
                            text = result.item.name,
                            style = MaterialTheme.typography.h6,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "List",
                            style = MaterialTheme.typography.caption
                        )
                    }
                    is SearchResult.TodoResult -> {
                        Text(
                            text = result.item.description,
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Todo Item",
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }
    }
}
