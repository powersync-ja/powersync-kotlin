package co.powersync.demos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import co.powersync.Database
import co.powersync.DatabaseDriverFactory

class MainActivity : ComponentActivity() {
    private val db = Database(DatabaseDriverFactory(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App(db)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(null)
}