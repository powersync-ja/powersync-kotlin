@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object BuildConfig {
    /*
      To debug on the JVM, you can:
      - Set the com.powersync.debug property with System.setProperty("com.powersync.debug", true) BEFORE calling any powersync API.
      - Start your java program with the -Dcom.powersync.debug=true command line argument.
      - Set the POWERSYNC_JVM_DEBUG environment variable to "true" before starting your program.
     */
    actual val isDebug: Boolean =
        System.getProperty("com.powersync.debug") == "true" ||
                System.getenv("POWERSYNC_JVM_DEBUG") == "true"
}
