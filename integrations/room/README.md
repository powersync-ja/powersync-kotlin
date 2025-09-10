# PowerSync Room integration

This module provides the ability to use PowerSync with Room databases. This module aims for complete
Room support, meaning that:

1. Changes synced from PowerSync automatically update your Room `Flow`s.
2. Room and PowerSync cooperate on the write connection, avoiding "database is locked errors".
3. Changes from Room trigger a CRUD upload.

## Setup

Add a dependency on `com.powersync:integration-room` with the same version you use for the main
PowerSync SDK.

PowerSync can use an existing Room database, provided that the PowerSync core SQLite extension has
been loaded. To do that:

1. Add a dependency on `androidx.sqlite:sqlite-bundled`. Using the SQLite version from the Android
   framework will not work as it doesn't support loading extensions.
2. On your `RoomDatabase.Builder`, call `setDriver()` with a PowerSync-enabled driver: 
    ```Kotlin
    val driver = BundledSQLiteDriver().also {
        it.loadPowerSyncExtension() // Extension method by this module
    }
    
    Room.databaseBuilder(...).setDriver(driver).build()
    ```
3. Configure raw tables for your Room databases.

After these steps, you can open your Room database like you normally would. Then, you can use the
following method to obtain  a `PowerSyncDatabase` instance that is backed by Room:

```Kotlin
val pool = RoomConnectionPool(yourRoomDatabase)
val powersync = PowerSyncDatabase.opened(
    pool = pool,
    scope = this,
    schema = Schema(...), // With Room, you need to use raw tables
    identifier = "databaseName", // Prefer to use the same path/name as your Room database
    logger = Logger,
)

powersync.connect(...)
```

Changes from PowerSync (regardless of whether they've been made with `powersync.execute` or from a
sync operation) will automatically trigger updates in Room.

To also transfer local writes to PowerSync, you need to

1. Create triggers on your Room tables to insert into `ps_crud` (see the PowerSync documentation on
   raw tables for details).
2. Listen for Room changes and invoke a helper method to transfer them to PowerSync:
    ```Kotlin
    yourRoomDatabase.getCoroutineScope().launch {
        // list all your tables here
        yourRoomDatabase.invalidationTracker.createFlow("users", "groups", /*...*/).collect {
            pool.transferRoomUpdatesToPowerSync()
        }
    }
    ```
