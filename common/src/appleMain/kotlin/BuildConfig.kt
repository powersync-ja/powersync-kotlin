import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object BuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = Platform.isDebugBinary
}
