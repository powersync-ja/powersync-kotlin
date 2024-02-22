import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory
import com.powersync.demos.RootContent

@Composable
fun MainView(factory: DatabaseDriverFactory) = RootContent(factory, Modifier.fillMaxSize())