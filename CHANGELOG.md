# Changelog

## 1.2.0 (pending)

* Add a new sync client implementation written in Rust instead of Kotlin. While this client is still
  experimental, we intend to make it the default in the future. The main benefit of this client is
  faster sync performance, but upcoming features will also require this client. We encourage 
  interested users to try it out by opting in to `ExperimentalPowerSyncAPI` and passing options when
  connecting:
  ```Kotlin
  //@file:OptIn(ExperimentalPowerSyncAPI::class)
  database.connect(MyConnector(), options = SyncOptions(
    newClientImplementation = true,
  ))
  ```
  Switching between the clients can be done at any time without compatibility issues. If you run
  into issues with the new client, please reach out to us!
* In addition to HTTP streams, the Kotlin SDK also supports fetching sync instructions from the
  PowerSync service in a binary format. This requires the new sync client, and can then be enabled
  on the sync options:
  ```Kotlin
  //@file:OptIn(ExperimentalPowerSyncAPI::class)
  database.connect(MyConnector(), options = SyncOptions(
    newClientImplementation = true,
    method = ConnectionMethod.WebSocket()
  ))
  ```
* [Android, JVM] Use version `0.4.0` of `powersync-sqlite-core`.

## 1.1.1

* Fix reported progress around compactions / defrags on the sync service.
* [Android] Set `temp_store_directory`, avoiding crashes for large materialized views.

## 1.1.0

* Add `trackPreviousValues` option on `Table` which sets `CrudEntry.previousValues` to previous values on updates.
* Add `trackMetadata` option on `Table` which adds a `_metadata` column that can be used for updates.
  The configured metadata is available through `CrudEntry.metadata`.
* Add `ignoreEmptyUpdates` option which skips creating CRUD entries for updates that don't change any values.

## 1.0.1

* [Internal] Version bump for broken Swift release pipeline

## 1.0.0

* Bump SDK to V1/Stable feature status
* Fixed `CrudBatch` `hasMore` always returning false.
* Added `triggerImmediately` to `onChange` method.
* Report real-time progress information about downloads through `SyncStatus.downloadProgress`.
* Compose: Add `composeState()` extension method on `SyncStatus`.
* [Internal] Added helper method for Swift `PowerSyncException` throwing.

## 1.0.0-BETA32

* Added `onChange` method to the PowerSync client. This allows for observing table changes.
* Removed unnecessary `User-Id` header from internal PowerSync service requests.
* Fix loading native PowerSync extension for Java targets.

## 1.0.0-BETA31

* Added helpers for Attachment syncing.
* Fix `getNextCrudTransaction()` only returning a single item.

## 1.0.0-BETA30

* Fix a deadlock when calling `connect()` immediately after opening a database.
  The issue has been introduced in version `1.0.0-BETA29`.

## 1.0.0-BETA29

* Fix potential race condition between jobs in `connect()` and `disconnect()`.
* [JVM Windows] Fixed PowerSync Extension temporary file deletion error on process shutdown.
* [iOS] Fixed issue where automatic driver migrations would fail with the error:

```
Sqlite operation failure database is locked attempted to run migration and failed. closing connection
```

* Fix race condition causing data received during uploads not to be applied.

## 1.0.0-BETA28

* Update PowerSync SQLite core extension to 0.3.12.
* Added queing protection and warnings when connecting multiple PowerSync clients to the same
  database file.
* Improved concurrent SQLite connection support accross various platforms. All platforms now use a
  single write connection and multiple read connections for concurrent read queries.
* Added the ability to open a SQLite database given a custom `dbDirectory` path. This is currently
  not supported on iOS due to internal driver restrictions.
* Internaly improved the linking of SQLite for iOS.
* Enabled Full Text Search on iOS platforms.
* Added the ability to update the schema for existing PowerSync clients.
* Fixed bug where local only, insert only and view name overrides were not applied for schema
  tables.
* The Android SQLite driver now uses
  the [Xerial JDBC library](https://github.com/xerial/sqlite-jdbc). This removes the requirement for
  users to add the jitpack Maven repository to their projects.

```diff
// settings.gradle.kts example
    repositories {
        google()
-        maven("https://jitpack.io") {
-            content { includeGroup("com.github.requery") }
-        }
        mavenCentral()
    }
```

## 1.0.0-BETA27

* Improved watch query internals. Added the ability to throttle watched queries.
* Fixed `uploading` and `downloading` sync status indicators.

## 1.0.0-BETA26

* Support bucket priorities and partial syncs.
* Android: Add ProGuard rules to prevent methods called through JNI from being minified or removed.

## 1.0.0-BETA25

* JVM: Lower minimum supported version from 17 to 8.

## 1.0.0-BETA24

* Improve internal handling of watch queries to avoid issues where updates are not being received
  due to transaction commits occurring after the query is run.
* Fix issue in JVM build where `columnNames` was throwing an error due to the index of the JDBC
  driver starting at 1 instead of 0 as in the other drivers/
* Throw and not just catch `CancellationExceptions` in `runWrappedSuspending`

## 1.0.0-BETA23

* Make `execute` and `PowerSyncTransaction` functions throwable for Swift

## 1.0.0-BETA22

* Fix `updateHasSynced` internal null pointer exception

## 1.0.0-BETA21

* Improve error handling for Swift by adding @Throws annotation so errors can be handled in Swift
* Throw PowerSync exceptions for all public facing methods

## 1.0.0-BETA20

* Add cursor optional functions: `getStringOptional`, `getLongOptional`, `getDoubleOptional`,
  `getBooleanOptional` and `getBytesOptional` when using the column name which allow for optional
  return types
* Throw errors for invalid column on all cursor functions
* `getString`, `getLong`, `getBytes`, `getDouble` and `getBoolean` used with the column name will
  now throw an error for non-null values and expect a non optional return type

## 1.0.0-BETA19

* Allow cursor to get values by column name e.g. `getStringOptional("id")`
* BREAKING CHANGE: If you were using `SqlCursor` from SqlDelight previously for your own custom
  mapper then you must now change to `SqlCursor` exported by the PowerSync module.

  Previously you would import it like this:

  ```kotlin
  import app.cash.sqldelight.db.SqlCursor
  ```

  now it has been changed to:

  ```kotlin
  import com.powersync.db.SqlCursor
  ```

## 1.0.0-BETA18

* BREAKING CHANGE: Move from async sqldelight calls to synchronous calls. This will only affect
  `readTransaction` and `writeTransaction`where the callback function is no longer asynchronous.

## 1.0.0-BETA17

* Add fix for Windows using JVM build

## 1.0.0-BETA16

* Add `close` method to database methods
* Throw when error is a `CancellationError` and remove invalidation for all errors in
  `streamingSync` catch.

## 1.0.0-BETA15

* Update powersync-sqlite-core to 0.3.8
* Increase maximum amount of columns from 63 to 1999

## 1.0.0-BETA14

* Add JVM compatibility
* Revert previous iOS changes as they resulted in further issues.

## 1.0.0-BETA13

* Move iOS database driver to use IO dispatcher which should avoid race conditions and improve
  performance.

## 1.0.0-BETA12

* Use transaction context in `writeTransaction` in `BucketStorageImpl`.

## 1.0.0-BETA11

* Update version to fix deployment issue of previous release

## 1.0.0-BETA10

* Change Swift package name from `PowerSync` to `PowerSyncKotlin`

## 1.0.0-BETA9

* Re-enable SKIE `SuspendInterop`
* Move transaction functions out of `PowerSyncTransactionFactory` to avoid threading issues in Swift
  SDK

## 1.0.0-BETA8

* Disable SKIE `SuspendInterop` plugin to fix overriding `suspend` functions in Swift

## 1.0.0-BETA7

* Update supabase connector to use supabase-kt version 3
* Handle Postgres error codes in supabase connector

## 1.0.0-BETA6

* Fix Custom Write Checkpoint application logic

## 1.0.0-BETA5

* Fix `hasSynced` not updating after `disconnectAndClear`
* Fix error being thrown in iOS app launch

## 1.0.0-BETA4

* Fix sync status being reset when `update` function is run

## 1.0.0-BETA3

* Add `waitForFirstSync` function - which resolves after the initial sync is completed
* Upgrade to Kotlin 2.0.20 - should not cause any issues with users who are still on Kotlin 1.9
* Upgrade `powersync-sqlite-core` to 0.3.0 - improves incremental sync performance
* Add client sync parameters - which allows you specify sync parameters from the
  client https://docs.powersync.com/usage/sync-rules/advanced-topics/client-parameters-beta

```kotlin
val params = JsonParam.Map(
    mapOf(
        "name" to JsonParam.String("John Doe"),
        "age" to JsonParam.Number(30),
        "isStudent" to JsonParam.Boolean(false)
    )
)

connect(
    ...
params = params
)
```

* Add schema validation when schema is generated
* Add warning message if there is a crudItem in the queue that has not yet been synced and after a
  delay rerun the upload

## 1.0.0-BETA2

* Publish persistence package

## 1.0.0-BETA1

* Improve API by changing from Builder pattern to simply instantiating the database
  `PowerSyncDatabase`
  E.g. `val db = PowerSyncDatabase(factory, schema)`
* Use callback context in transactions
  E.g. `db.writeTransaction{ ctx -> ctx.execute(...) }`
* Removed unnecessary expiredAt field
* Added table max column validation as there is a hard limit of 63 columns
* Moved SQLDelight models to a separate module to reduce export size
* Replaced default Logger with [Kermit Logger](https://kermit.touchlab.co/) which allows users to
  more easily use and/or change Logger settings
* Add `retryDelay` and `crudThrottle` options when setting up database connection
* Changed `_viewNameOverride` to `viewNameOverride`
