import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

public actual object BuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    public actual val isDebug: Boolean = Platform.isDebugBinary
}