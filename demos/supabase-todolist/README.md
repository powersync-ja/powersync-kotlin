# PowerSync + Supabase Kotlin Multiplatform Demo: Todo List App

It is a simple to-do list application demonstrating use of the PowerSync Kotlin Mutiplatform SDK together
with [Supabase](https://supabase.com/) in a basic Kotlin Multiplatform Compose App.

Supported KMP targets: Android, iOS and Desktop (JVM).

## Setting up your development environment

To setup your environment, please consult [these instructions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html).

## Set up Supabase project

A step-by-step guide on Supabase<>PowerSync integration is available [here](https://docs.powersync.com/integration-guides/supabase).

### Opting in to priorities

If you want to use the example with [bucket priorities](https://docs.powersync.com/usage/use-case-examples/prioritized-sync),
you can adopt the following sync rules instead of the ones suggested by the simpler integration guide:

```YAML
bucket_definitions:
  all_lists:
    priority: 1
    parameters: select request.user_id() as "user"
    data:
      - select * from lists where owner_id = bucket."user"

  list_items:
    # Separate bucket per list
    parameters: select id as list_id from lists where owner_id = request.user_id()
    data:
      - select * from todos where list_id = bucket.list_id
```

The project will work with both sync rules, but giving lists a higher priority allows updates to be synchronized before
all items have been received.

## Configure project in Android Studio

1. Clone this repo: ```git clone https://github.com/powersync-ja/powersync-kotlin.git```
2. Open the repo in Android Studio. This creates a `local.properties` file in root and should contain a `sdk.dir=/path/to/android/sdk` line.
3. Sync the project with Gradle (this should happen automatically, or choose File > Sync project with Gradle Files).
4. Insert your Supabase project URL, Supabase Anon Key, and PowerSync instance URL into the `demos/supabase-todlist/local.properties` file:

```bash
# local.properties
sdk.dir=/path/to/android/sdk

# Enter your PowerSync instance URL
POWERSYNC_URL=https://foo.powersync.journeyapps.com
# Enter your Supabase project's URL and public anon key (Project settings > API)
SUPABASE_URL=https://foo.supabase.co
SUPABASE_ANON_KEY=foo
```

## Run the app

Choose a run configuration for the Android or iOS target in Android Studio and run it.

For Android, this demo contains two Android apps:

- [`androidApp/`](androidApp/): This is a regular compose UI app using PowerSync.
- [`androidBackgroundSync/`](androidBackgroundSync/): This example differs from the regular app in
  that it uses a foreground service managing the synchronization process. The service is started
  in the main activity and keeps running even after the app is closed.
  For more notes on background sync, see [this document](docs/BackgroundSync.md).
