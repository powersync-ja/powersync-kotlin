<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/19345049/602bafa0-41ce-4cee-a432-56848c278722"/></a>
</p>

[PowerSync](https://powersync.com) is a service and set of SDKs that keeps Postgres databases in sync with on-device SQLite databases.

# PowerSync Kotlin Multiplatform SDK

## Alpha release

This SDK is currently in an alpha release. If you find a bug or issue, please open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin/issues). Questions or feedback can be posted on
our [community Discord](https://discord.gg/powersync) - we'd love to hear from you.

## SDK Features

* Provides real-time streaming of database changes, using Kotlin Coroutines and Flows.
* Offers direct access to the SQLite database, enabling the use of SQL on both client and server
  sides.
* Operations are asynchronous, ensuring the user interface remains unblocked.
* Supports concurrent database operations, allowing one write and multiple reads simultaneously.
* Enables subscription to queries for receiving live updates.
* Eliminates the need for client-side database migrations as these are managed automatically.

Supported KMP targets: Android and iOS.

## Structure: Packages

- [core](./core/README.md)

    - Kotlin Multiplatform SDK implementation

- [connectors](./connectors/README.md)

    - Supabase connector

## Demo Apps / Example Projects

The easiest way to test the PowerSync KMP SDK is to run one of our demo applications.

Demo applications are located in the [`demos/`](./demos) directory. See their respective README's for testing instructions:

- [demos/hello-powersync](./demos/hello-powersync/README.md): A minimal example demonstrating the use of the PowerSync Kotlin Multiplatform SDK.

- [demos/supabase-todolist](./demos/supabase-todolist/README.md): ** Currently a work in progress **
  A simple to-do list application that uses the PowerSync Kotlin Multiplatform SDK and the Supabase connector.

## Limitations

The PowerSync Kotlin Multiplatform SDK is currently in alpha release and is not yet suitable for production use.

- Integration with SQLDelight schema and API generation is not yet supported.
- Sqlite database migration is not yet supported.
- Configurable logging is not yet implemented.

## Getting Started

### Installation

Add the PowerSync Kotlin Multiplatform SDK to your project by adding the following to your `build.gradle.kts` file:

```kotlin

kotlin {
    //...
  sourceSets {
    commonMain.dependencies {
      api("com.powersync:core:$powersyncVersion")
    }
    //...
  }
}
```

If want to use the Supabase Connector, also add the following to `commonMain.dependencies`:

```kotlin
    implementation("com.powersync:connectors:$powersyncVersion")
```

#### Cocoapods

When using the PowerSync Kotlin Multiplatform SDK to build CocoaPods iOS, add the following to the `cocoapods` config in your `build.gradle.kts`:

```kotlin
cocoapods {
  //...
  pod("powersync-sqlite-core") {
    linkOnly = true
  }

  framework {
    isStatic = true
    export("com.powersync:core")
  }
  //...
}
```
Note: The `linkOnly` attribute is set to `true` and framework is set to `isStatic = true` to ensure that the `powersync-sqlite-core` binaries are only statically linked.

### Usage

The first step is creating a PowerSync account and setting up a PowerSync instance. If you are using Supabase, we have a step-by-step tutorial
available [here](https://docs.powersync.com/integration-guides/supabase-+-powersync). For other Postgres backend providers, follow these steps:

* Sign up for a free PowerSync account
  here [https://www.powersync.com/](https://www.powersync.com/).
* Visit the [PowerSync dashboard](https://powersync.journeyapps.com/) to create a PowerSync instance. After signing up you will be prompted to start the onboarding wizard which guides your though the
  steps required for this, and find database specific
  instructions [here](https://docs.powersync.com/usage/installation/database-setup). Existing users: start the onboarding wizard by navigating to Help > Start guide in the top-right corner.

#### Implement a backend connector and initialize the PowerSync database

1. Define the schema for the on-device SQLite database.

```kotlin
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val schema: Schema = Schema(
  listOf(
    Table(
      "customers",
      listOf(
        Column.text("name"),
        Column.text("email")
      )
    )
  )
)

```
Note: No need to declare a primary key `id` column, as PowerSync will automatically create this.

2. Implement a backend connector to define how PowerSync communicates with your backend this sends changes in local data to your backend service.
    
```kotlin
class MyConnector: PowerSyncBackendConnector() {
  override suspend fun fetchCredentials(): PowerSyncCredentials {
    // implement fetchCredentials to obtain the necessary credentials to connect to your backend
  }

  override suspend fun uploadData(database: PowerSyncDatabase) {
    // Implement uploadData to send local changes to your backend service
    // You can omit this method if you only want to sync data from the server to the client
    // see https://docs.powersync.com/usage/installation/upload-data
  }
}
```

Alternatively, you can use [SupabaseConnector.kt](./connectors/src/commonMain/kotlin/com/powersync/connectors/SupabaseConnector.kt) as a starting point.

3. Initialize the PowerSync database an connect it to the connector, using `PowerSyncBuilder`:
  a. Create platform specific `DatabaseDriverFactory` to be used by the `PowerSyncBuilder` to create the SQLite database driver.
  ```kotlin
  // Android
  val driverFactory = DatabaseDriverFactory(this)
  
  // iOS
  val driverFactory = DatabaseDriverFactory()
  ```

  b. Build a `PowerSyncDatabase` instance using the `PowerSyncBuilder`, schema and the `DatabaseDriverFactory`:
  ```kotlin
    // commonMain
    val database = PowerSyncBuilder.from(driverFactory, AppSchema).build()
  ```

  c. Connect the `PowerSyncDatabase` to the backend connector:
  ```kotlin
    // commonMain
    database.connect(MyConnector())
  ```

4. Subscribe to changes in data
    
```kotlin
fun watchCustomers(): Flow<List<User>> {
  return database.watch("SELECT * FROM customers", mapper = { cursor ->
    User(
      id = cursor.getString(0)!!,
      name = cursor.getString(1)!!,
      email = cursor.getString(2)!!
    )
  })
}
```

5. Insert, update, and delete data in the SQLite database

```kotlin
suspend fun insertCustomer(name: String, email: String) {
  database.writeTransaction {
    database.execute(
      "INSERT INTO customers (id, name, email) VALUES (uuid(), ?, ?)",
      listOf(name, email)
    )
  }
}

suspend fun updateCustomer(id: String, name: String, email: String) {
    database.execute(
      "UPDATE customers SET name = ? WHERE email = ?",
      listOf(name, email)
    )
}

suspend fun deleteCustomer(id: String? = null) {
    // If no id is provided, delete the first customer in the database
  val targetId =
    id ?: database.getOptional("SELECT id FROM customers LIMIT 1", mapper = { cursor ->
      cursor.getString(0)!!
    })
    ?: return

  database.writeTransaction {
    database.execute("DELETE FROM customers WHERE id = ?", listOf(targetId))
  }
}
```

## Development

### Build

[//]: # (TODO)

### Publishing

[//]: # (TODO)