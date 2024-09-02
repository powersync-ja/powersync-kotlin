# PowerSync core module

The PowerSync core module provides the core functionality for the PowerSync Kotlin Multiplatform SDK.

## Structure

This is a Kotlin Multiplatform project targeting Android, iOS platforms, with the following structure:

- `commonMain` - Shared code for all targets, which includes the `PowerSyncBackendConnector` interface and `PowerSyncBuilder` for building a `PowerSync` instance. It also defines
  the `DatabaseDriverFactory` class to be implemented in each platform.
- `androidMain` - Android specific code, which includes a implementation of `DatabaseDriverFactory` class that creates an instance of `app.cash.sqldelight.driver.android.AndroidSqliteDriver` using
  a `io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory`. It also includes native SQLite bindings for Android.
- `iosMain` - iOS specific code, which includes a implementation of `DatabaseDriverFactory` class that creates an instance of `app.cash.sqldelight.driver.native.NativeSqliteDriver` and also sets up native SQLite bindings for iOS.

## Note on SQLDelight

The PowerSync core module, internally makes use of [SQLDelight](https://cashapp.github.io/sqldelight) for it database API and typesafe database query generation.

With the Beta release, the PowerSync core module does not support integrating with SQLDelight from client applications.
