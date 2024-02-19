# Hello-PowerSync Demo App

This is a simple demo app demonstrating use of the [PowerSync Kotlin Mutiplatform SDK](#) together with [Supabase](https://supabase.com/). 

Supported targets: Android and iOS.

## Alpha release

The Kotlin Multiplatform SDK is currently in an alpha release. If you find a bug or issue, please open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin-sdk/issues). Questions or feedback can be posted on our [community Discord](https://discord.gg/powersync) - we'd love to hear from you.

## Getting started
TODO: These instructions need to be adapted for Kotlin:

In your terminal, switch into the demo's directory:

```
cd demos/hello-powersync
```

Set up the necessary environment variables. Copy the .env.local.template file:

```
cp .env.local.template .env.local
```

And then edit .env.local to insert your credentials for Supabase and your PowerSync instance.

Run on Android:

```
pnpm android
```

Run on iOS:

```
pnpm ios
```

## Project structure

This is a Kotlin Multiplatform project targeting Android and iOS. 

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - `commonMain` is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the
      folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for
  your project.

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)