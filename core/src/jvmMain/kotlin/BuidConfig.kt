public actual object BuildConfig {
    public actual val isDebug: Boolean
        // TODO: need to determine a good way to set this on JVM presuming we don't want to bundle BuildKonfig in the
        //  library.
        get() = true
}