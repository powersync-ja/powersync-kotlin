import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory
import com.powersync.demos.App

@Composable fun MainView() = App(DatabaseDriverFactory(), Modifier.fillMaxSize())
