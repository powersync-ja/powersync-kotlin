@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object BuildConfig {
    actual val isDebug: Boolean
        get() = com.powersync.common.BuildConfig.DEBUG
}
