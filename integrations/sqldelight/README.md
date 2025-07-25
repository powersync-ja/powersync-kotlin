## PowerSync SQLDelight driver

This library provides the `PowerSyncDriver` class, which implements an `SqlDriver` for `SQLDelight`
backed by PowerSync.

Usage:

```kotlin
val db: PowerSyncDatabase = openPowerSyncDatabase()
val yourSqlDelightDatabase = YourDatabase(PowerSyncDriver(db))
```

Afterwards, writes on both databases (the original `PowerSyncDatabase` instance and the SQLDelight
database) will be visible to each other, update each other's query flows and will be synced
properly.

## Limitations

Please note that this library is currently in alpha. It is tested, but API changes are still
possible.

There are also some limitations to be aware of:

1. Due to historical reasons, the PowerSync SDK migrates all databases to `user_version` 1 when
   created (but it will never downgrade a database).
   So if you want to use SQLDelight's schema tools, the first version would have to be `2`.
2. While you can write `CREATE TABLE` statements in your `.sq` files, note that these aren't
   actually used - you still have to define your PowerSync schema and the SDK will auto-create the
   tables from there.
3. Functions and tables contributed by the PowerSync core extension are not visible to `.sq` files
   at the moment. We might revisit this with a custom dialect in the future.
