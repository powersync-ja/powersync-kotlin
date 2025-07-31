<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/7372448/d2538c43-c1a0-4c47-9a76-41462dba484f"/></a>
</p>

*[PowerSync](https://www.powersync.com) is a sync engine for building local-first apps with instantly-responsive UI/UX and simplified state transfer. Syncs between SQLite on the client-side and Postgres, MongoDB or MySQL on the server-side.*

# PowerSync Kotlin Multiplatform SDK

This is the PowerSync client SDK for Kotlin. This SDK currently supports the following Kotlin targets:

- Android
- JVM
- iOS
- macOS
- watchOS

If you need support for additional targets, please reach out!

See a summary of features [here](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform#sdk-features)
and API documentation [here](https://powersync-ja.github.io/powersync-kotlin/).

## Structure: Packages

- [core](./core/)

    - This is the Kotlin Multiplatform SDK implementation.

- [connectors](./connectors/)

    - [SupabaseConnector.kt](./connectors/supabase/src/commonMain/kotlin/com/powersync/connector/supabase/SupabaseConnector.kt) An example connector implementation for Supabase (Postgres). The backend
      connector provides the connection between your application backend and the PowerSync managed database. It is used to:
        1. Retrieve a token to connect to the PowerSync service.
        2. Apply local changes on your backend application server (and from there, to your backend database).

## Demo Apps / Example Projects

The easiest way to test the PowerSync KMP SDK is to run one of our demo applications.

Demo applications are located in the [`demos/`](./demos) directory. See their respective README's for testing instructions:

- [demos/hello-powersync](./demos/hello-powersync/README.md): A minimal example demonstrating the use of the PowerSync Kotlin Multiplatform SDK and the Supabase connector.

- [demos/supabase-todolist](./demos/supabase-todolist/README.md): A simple to-do list application demonstrating the use of the PowerSync Kotlin Multiplatform SDK and the Supabase connector.
- [demos/android-supabase-todolist](./demos/android-supabase-todolist/README.md): A simple to-do list application demonstrating the use of the PowerSync Kotlin Multiplatform SDK and the Supabase connector in an Android application.

## Current Limitations / Future work

Current limitations:

- Integration with SQLDelight schema and API generation (ORM) is not yet supported.

## Installation

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

### Cocoapods

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

## Formatting and Linting

This repo uses [ktlint](https://pinterest.github.io/ktlint/) to handle formatting and linting. If you would like the IDE to automatically format your code and show linting errors install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint). Then in Settings go to Tools -> Ktlint -> Select Distract free (recommended) mode.
It will automatically use the rules set in the `.editorconfig` file.

## Getting Started

Our [full SDK reference](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform-alpha#getting-started) contains everything you need to know to get started implementing PowerSync in your project.

## Examples

For example projects built with PowerSync and Kotlin Multiplatform, see our [Demo Apps / Example Projects](https://docs.powersync.com/resources/demo-apps-example-projects#kotlin-multiplatform) gallery. Most of these projects can also be found in the [`demos/`](demos/) directory.
