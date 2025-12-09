## PowerSync with SQLite3MultipleCiphers

This is a cross-platform build of a SQLite driver using [SQLite3MultipleCiphers](https://utelle.github.io/SQLite3MultipleCiphers/)
for the PowerSync Kotlin SDK.

> [!NOTE]
> Note that this package is currently in alpha.

## Using

This package is designed to be used with the main PowerSync SDK. On native platforms, it is incompatible with the
default `com.powersync:core` though.
To use this package, replace your dependency on `com.powersync:core` with `com.powersync:common` of the same version.
Also add a dependency on `com.powersync:sqlite3multipleciphers`.
Finally, note that `:core` has a dependency on a ktor client implementation for each platform. So if you're not using
ktor in your project already, you'd have to add:

1. A dependency on `io.ktor:ktor-client-okhttp` on Android and the JVM.
2. A dependency on `io.ktor:ktor-client-darwin` for Apple targets on Kotlin/Native.

Different client implementations are also available, see [the overview](https://ktor.io/docs/client-engines.html#platforms)
for details.

You can continue to use `PowerSyncDatabase` to open databases. For the `factory` parameter, pass:

- On Android: An instance of `AndroidEncryptedDatabaseFactory`.
- For JVM targets: An instance of `JavaEncryptedDatabaseFactory`.
- For Kotlin/Native: An instance of `NativeEncryptedDatabaseFactory`.

Each of these factories takes a `Key` instance used to encrypt the database.

## Credits

The Android / Java sources of this package, as well as the JNI wrapper code, have been copied and adapted from
the `androidx.sqlite:bundled-sqlite-driver` package.
