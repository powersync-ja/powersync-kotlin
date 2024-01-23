package co.powersync.demos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import co.powersync.db.DatabaseDriverFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App(PowerSync(DatabaseDriverFactory(this)))
        }
    }
}


@Preview
@Composable
fun ViewContentPreview() {
    ViewContent("Preview", listOf(User("1", "John Doe", "john@example.com")), {}, {})
}

@Preview
@Composable
fun ViewContentPreview_ListItem() {
    ListItem(user = User("1", "John Doe", "john@example.com"))
}

@Preview
@Composable
fun ViewContentPreview_MyButton() {
    MyButton(label = "Preview Button", onClick = {})
}

