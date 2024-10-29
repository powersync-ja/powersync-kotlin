# Changelog

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
* Add client sync parameters - which allows you specify sync parameters from the client https://docs.powersync.com/usage/sync-rules/advanced-topics/client-parameters-beta
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
* Add warning message if there is a crudItem in the queue that has not yet been synced and after a delay rerun the upload

## 1.0.0-BETA2

* Publish persistence package

## 1.0.0-BETA1

* Improve API by changing from Builder pattern to simply instantiating the database `PowerSyncDatabase`
  E.g. `val db = PowerSyncDatabase(factory, schema)`
* Use callback context in transactions
  E.g. `db.writeTransaction{ ctx -> ctx.execute(...) }`
* Removed unnecessary expiredAt field
* Added table max column validation as there is a hard limit of 63 columns
* Moved SQLDelight models to a separate module to reduce export size
* Replaced default Logger with [Kermit Logger](https://kermit.touchlab.co/) which allows users to more easily use and/or change Logger settings
* Add `retryDelay` and `crudThrottle` options when setting up database connection
* Changed `_viewNameOverride` to `viewNameOverride`
