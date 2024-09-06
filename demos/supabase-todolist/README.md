# PowerSync + Supabase Kotlin Multiplatform Demo: Todo List App

It is a simple to-do list application demonstrating use of the PowerSync Kotlin Mutiplatform SDK together
with [Supabase](https://supabase.com/) in a basic Kotlin Multiplatform Compose App.

Supported KMP targets: Android and iOS.

## Setting up your development environment

To setup your environment, please consult [these instructions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html).

## Set up Supabase project

A step-by-step guide on Supabase<>PowerSync integration is available [here](https://docs.powersync.com/integration-guides/supabase).

## Configure project in Android Studio

1. Clone this repo: ```git clone https://github.com/powersync-ja/powersync-kotlin.git```
2. Open the repo in Android Studio. This creates a `local.properties` file in root and should contain a `sdk.dir=/path/to/android/sdk` line.
3. Sync the project with Gradle (this should happen automatically, or choose File > Sync project with Gradle Files).
4. Open the `demos/supabase-todolist` directory in Android Studio and sync this project with Gradle.
5. Insert your Supabase project, auth user, and PowerSync project credentials into the `local.properties` file:

```bash
# local.properties
sdk.dir=/path/to/android/sdk

# Enter your PowerSync instance URL
POWERSYNC_URL=https://foo.powersync.journeyapps.com
# Enter your Supabase project's URL and public anon key (Project settings > API)
SUPABASE_URL=https://foo.supabase.co
SUPABASE_ANON_KEY=foo

# Enter your Supabase auth user's details
SUPABASE_USER_EMAIL=user@example.com
SUPABASE_USER_PASSWORD=foo
```

## Run the app

Choose a run configuration for the Android or iOS target in Android Studio and run it.
