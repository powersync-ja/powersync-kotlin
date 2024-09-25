<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/7372448/d2538c43-c1a0-4c47-9a76-41462dba484f"/></a>
</p>

*[PowerSync](https://www.powersync.com) is a sync engine for building local-first apps with instantly-responsive UI/UX and simplified state transfer.*

# PowerSync Kotlin Multiplatform SDK

This is the PowerSync client SDK for Kotlin Mutliplatform. This SDK currently supports Android and iOS as targets.

See a summary of features [here](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform-alpha#sdk-features).

## Beta release

This SDK is currently in a beta release. If you find a bug or issue, please open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin/issues). Questions or feedback can be posted on
our [community Discord](https://discord.gg/powersync) - we'd love to hear from you.

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

## Current Limitations / Future work

The PowerSync Kotlin Multiplatform SDK is currently in a beta release. It can be used in production if you’ve tested your use cases.

Current limitations:

- Integration with SQLDelight schema and API generation (ORM) is not yet supported.
- Supports only a single database file.

Future work/ideas:

- Improved error handling.
- Attachments helper package.
- Management of DB connections on each platform natively.
- Supporting additional targets (JVM, Wasm).

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

## Getting Started

Our [full SDK reference](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform-alpha#getting-started) contains everything you need to know to get started implementing PowerSync in your project.

## Examples

For example projects built with PowerSync and Kotlin Multiplatform, see our [Demo Apps / Example Projects](https://docs.powersync.com/resources/demo-apps-example-projects#kotlin-multiplatform) gallery. Most of these projects can also be found in the [`demos/`](demos/) directory.
