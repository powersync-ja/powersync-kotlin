package com.powersync.demos

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val MARGIN_SCROLLBAR: Dp = 0.dp

internal interface ScrollbarAdapter

@Composable
internal fun rememberScrollbarAdapter(scrollState: LazyListState): ScrollbarAdapter =
    object : ScrollbarAdapter {}

@Composable
internal fun VerticalScrollbar(
    modifier: Modifier,
    adapter: ScrollbarAdapter
) {
    // no-op
}



internal fun Modifier.onKeyUp(key: Key, action: () -> Unit): Modifier =
    onKeyEvent { event ->
        if ((event.type == KeyEventType.KeyUp) && (event.key == key)) {
            action()
            true
        } else {
            false
        }
    }
