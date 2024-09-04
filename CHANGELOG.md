# Changelog

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
