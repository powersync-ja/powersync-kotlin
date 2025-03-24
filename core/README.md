# PowerSync core module

The PowerSync core module provides the core functionality for the PowerSync Kotlin Multiplatform
SDK.

## Structure

This is a Kotlin Multiplatform project targeting Android, iOS platforms, with the following
structure:

- `commonMain` - Shared code for all targets, which includes the `PowerSyncBackendConnector`
  interface and `PowerSyncBuilder` for building a `PowerSync` instance. It also defines
  the `DatabaseDriverFactory` class to be implemented in each platform.
- `commonJava` - Common Java code including a Java SQLite driver using
  the [Xerial JDBC Driver](https://github.com/xerial/sqlite-jdbc). This is used by both the Android
  and JVM drivers.
- `androidMain` - Android specific code, which includes an implementation of
  `DatabaseDriverFactory`.
- `jvmMain` - JVM specific code which includes an implementation of `DatabaseDriverFactory`.
- `iosMain` - iOS specific code, which includes am implementation of `DatabaseDriverFactory` class
  that creates an instance of `app.cash.sqldelight.driver.native.NativeSqliteDriver` and also sets
  up native SQLite bindings for iOS.

## Note on SQLDelight

The PowerSync core module, internally makes use
of [SQLDelight](https://sqldelight.github.io/sqldelight/latest/) for it database API and typesafe database
query generation.

The PowerSync core module does not currently support integrating with SQLDelight from client
applications.
