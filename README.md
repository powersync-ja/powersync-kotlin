<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/7372448/d2538c43-c1a0-4c47-9a76-41462dba484f"/></a>
</p>

*[PowerSync](https://www.powersync.com) is a Postgres-SQLite sync layer, which helps developers to create local-first real-time reactive apps that work seamlessly both online and offline.*

# PowerSync Kotlin Multiplatform SDK

## Beta release

This SDK is currently in an beta release. If you find a bug or issue, please open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin/issues). Questions or feedback can be posted on
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

- [core](./core/)

    - This is the Kotlin Multiplatform SDK implementation.

- [connectors](./connectors/)

    - [SupabaseConnector.kt](./connectors/supabase/src/commonMain/kotlin/com/powersync/connector/supabase/SupabaseConnector.kt) An example connector implementation tailed for Supabase. The backend
      connector provides
      the connection between your application backend and the PowerSync managed database. It is used to:
        1. Retrieve a token to connect to the PowerSync service.
        2. Apply local changes on your backend application server (and from there, to Postgres).

## Demo Apps / Example Projects

The easiest way to test the PowerSync KMP SDK is to run one of our demo applications.

Demo applications are located in the [`demos/`](./demos) directory. See their respective README's for testing instructions:

- [demos/hello-powersync](./demos/hello-powersync/README.md): A minimal example demonstrating the use of the PowerSync Kotlin Multiplatform SDK and the Supabase connector.

- [demos/supabase-todolist](./demos/supabase-todolist/README.md): ** Currently a work in progress **
  A simple to-do list application demonstrating the use of the PowerSync Kotlin Multiplatform SDK and the Supabase connector.

## Current Limitations / Future work

The PowerSync Kotlin Multiplatform SDK is currently in a beta release and is not yet suitable for production use.

Current limitations:

- Integration with SQLDelight schema and API generation (ORM) is not yet supported.
- Supports only a single database file.

Future work/ideas:

- Improved error handling.
- Attachments helper package.
- Management of DB connections on each platform natively.
- Supporting additional targets (JVM, Wasm).

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
    implementation("com.powersync:connector-supabase:$powersyncVersion")
```

#### Cocoapods

We recommend using Cocoapods (as opposed to SMP) for iOS targets. Add the following to the `cocoapods` config in your `build.gradle.kts`

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
available [here](https://docs.powersync.com/integration-guides/supabase-+-powersync).

For other Postgres backend providers, follow these steps:

* Sign up for a free PowerSync account
  here [https://www.powersync.com/](https://www.powersync.com/).
* Visit the [PowerSync dashboard](https://powersync.journeyapps.com/) to create a PowerSync instance. After signing up you will be prompted to start the onboarding wizard which guides your though the
  steps required for this, and find database specific
  instructions [here](https://docs.powersync.com/usage/installation/database-setup). Existing users: start the onboarding wizard by navigating to Help > Start guide in the top-right corner.
* Developer documentation for PowerSync is available [here](https://docs.powersync.com/).

#### 1. Define the schema for the on-device SQLite database.

You need to set up your schema in your app project. This involves defining your schema in code using the PowerSync syntax.
This schema represents a "view" of the downloaded data. No migrations are required — the schema is applied directly when the PowerSync database is constructed.

```kotlin
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val schema: Schema = Schema(
    Table(
        name = "customers",
        columns = listOf(
            Column.text("name"),
            Column.text("email")
        )
    )
)

```

Note: No need to declare a primary key `id` column, as PowerSync will automatically create this.

#### 2. Implement a backend connector to define how PowerSync communicates with your backend.

The PowerSync backend connector provides the connection between your application backend and the PowerSync managed database.
It is used to:

1. Retrieve a token to connect to the PowerSync instance.
2. Apply local changes on your backend application server (and from there, to Postgres)

If you are using Supabase, you can use [SupabaseConnector.kt](./connectors/supabase/src/commonMain/kotlin/com/powersync/connector/supabase/SupabaseConnector.kt) as a starting point.

```kotlin
class MyConnector : PowerSyncBackendConnector() {
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        // implement fetchCredentials to obtain the necessary credentials to connect to your backend
        // See an example implementation in connectors/supabase/src/commonMain/kotlin/com/powersync/connector/supabase/SupabaseConnector.kt
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        // Implement uploadData to send local changes to your backend service
        // You can omit this method if you only want to sync data from the server to the client
        // See an example implementation in connectors/supabase/src/commonMain/kotlin/com/powersync/connector/supabase/SupabaseConnector.kt
        // See https://docs.powersync.com/usage/installation/app-backend-setup/writing-client-changes for considerations.
    }
}
```

#### 3. Initialize the PowerSync database and connect it to the connector, using `PowerSyncBuilder`:

You need to instantiate the PowerSync database — this is the core managed database.
Its primary functions are to record all changes in the local database, whether online or offline. In addition, it automatically uploads changes to your app backend when connected.

a. Create platform specific `DatabaseDriverFactory` to be used by the `PowerSyncBuilder` to create the SQLite database driver.

  ```kotlin
// Android
val driverFactory = DatabaseDriverFactory(this)

// iOS
val driverFactory = DatabaseDriverFactory()
  ```

b. Build a `PowerSyncDatabase` instance using the `PowerSyncBuilder` and the `DatabaseDriverFactory`. The schema you created in a previous step is also used as a parameter:

  ```kotlin
// commonMain
val database = PowerSyncBuilder.from(driverFactory, schema).build()
  ```

c. Connect the `PowerSyncDatabase` to the backend connector:

  ```kotlin
// commonMain
database.connect(MyConnector())
  ```

**Special case: Compose Multiplatform**

The artifact `com.powersync:powersync-compose` provides a simpler API:

```kotlin
// commonMain
val database = rememberPowerSyncDatabase(schema)
remember {
    database.connect(MyConnector())
}
```

#### 4. Subscribe to changes in data

```kotlin
// You can watch any SQL query. This excutes a read query every time the source tables are modified.
fun watchCustomers(): Flow<List<User>> {
    // TODO: implement your UI based on the result set
    return database.watch("SELECT * FROM customers", mapper = { cursor ->
        User(
            id = cursor.getString(0)!!,
            name = cursor.getString(1)!!,
            email = cursor.getString(2)!!
        )
    })
}
```

#### 5. Insert, update, and delete data in the local database

The `execute` method executes a write query (INSERT, UPDATE, DELETE) and returns the results (if any).

```kotlin
suspend fun insertCustomer(name: String, email: String) {
    database.writeTransaction { tx ->
        tx.execute(
            sql = "INSERT INTO customers (id, name, email) VALUES (uuid(), ?, ?)",
            parameters = listOf(name, email)
        )
    }
}

suspend fun updateCustomer(id: String, name: String, email: String) {
    database.execute(
        sql = "UPDATE customers SET name = ? WHERE email = ?",
        parameters = listOf(name, email)
    )
}

suspend fun deleteCustomer(id: String? = null) {
    // If no id is provided, delete the first customer in the database
    val targetId =
        id ?: database.getOptional(
            sql = "SELECT id FROM customers LIMIT 1",
            mapper = { cursor ->
                cursor.getString(0)!!
            }
        ) ?: return

    database.writeTransaction { tx ->
        tx.execute(
            sql = "DELETE FROM customers WHERE id = ?",
            parameters = listOf(targetId)
        )
    }
}
```
