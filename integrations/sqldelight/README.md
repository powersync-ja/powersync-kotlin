## PowerSync SQLDelight driver

This library provides the `PowerSyncDriver` class, which implements an `SqlDriver` for `SQLDelight`
backed by PowerSync.

## Setup

Add a dependency on `com.powersync:integration-sqldelight`, using the same version you use for the
PowerSync SDK.

## Usage

To get started, ensure that SQLDelight is not linking sqlite3 (the PowerSync SDK takes care of that,
and you don't want to link it twice). Also, ensure the async generator is active because the
PowerSync driver does not support synchronous reads:

```kotlin
sqldelight {
    databases {
        linkSqlite.set(false)

        create("MyAppDatabase") {
            generateAsync.set(true)
            deriveSchemaFromMigrations.set(false)

            dialect("app.cash.sqldelight:sqlite-3-38-dialect")
        }
    }
}
```

Next, define your tables in `.sq` files (but note that the `CREATE TABLE` statement won't be used,
PowerSync creates JSON-backed views for tables instead).
Open a PowerSync database [in the usual way](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform#getting-started)
and finally pass it to the constructor of your generated SQLDelight database:

```kotlin
val db: PowerSyncDatabase = openPowerSyncDatabase()
val yourSqlDelightDatabase = YourDatabase(PowerSyncDriver(db))
```

Afterwards, writes on both databases (the original `PowerSyncDatabase` instance and the SQLDelight
database) will be visible to each other, update each other's query flows and will get synced
properly.

## Limitations

Please note that this library is currently in alpha. It is tested, but API changes are still
possible.

There are also some limitations to be aware of:

1. Due to historical reasons, the PowerSync SDK migrates all databases to `user_version` 1 when
   created (but it will never downgrade a database).
   So if you want to use SQLDelight's schema tools, the first version would have to be `2`.
2. The `CREATE TABLE` statements in your `.sq` files are only used at build time to verify your
   queries. At runtime, PowerSync will create tables from your schema as views, the defined
   statements are ignored.
   If you want to use the schema managed by SQLDelight, configure PowerSync to use
   [raw tables](https://docs.powersync.com/usage/use-case-examples/raw-tables).
3. Functions and tables contributed by the PowerSync core extension are not visible to `.sq` files
   at the moment. We might revisit this with a custom dialect in the future.
