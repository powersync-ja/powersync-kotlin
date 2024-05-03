package com.powersync.demos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.powersync.DatabaseDriverFactory
import com.powersync.sync.SyncStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}


@Preview
@Composable
fun ViewContentPreview() {
    ViewContent("Preview", listOf(User("1", "John Doe", "john@example.com")), {}, {}, {}, {}, SyncStatus.empty())
}

@Preview
@Composable
fun ViewContentPreview_ListItem() {
    ListItem(cols = arrayOf("John Doe", "john@example.com"))
}

@Preview
@Composable
fun ViewContentPreview_MyButton() {
    MyButton(label = "Preview Button", onClick = {})
}

