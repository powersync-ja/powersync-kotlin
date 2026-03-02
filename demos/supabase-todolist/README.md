# PowerSync + Supabase Kotlin Multiplatform Demo: Todo List App

It is a simple to-do list application demonstrating use of the PowerSync Kotlin Mutiplatform SDK together
with [Supabase](https://supabase.com/) in a basic Kotlin Multiplatform Compose App.

Supported KMP targets: Android, iOS and Desktop (JVM).

## Setting up your development environment

To setup your environment, please consult [these instructions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html).

## Set up Supabase project

To run this demo, you need a Supabase and PowerSync project. Detailed instructions for integrating PowerSync with Supabase can be found in [the integration guide](https://docs.powersync.com/integration-guides/supabase).

Follow this guide to:
1. Create and configure a Supabase project.
2. Create a new PowerSync instance, connecting to the database of the Supabase project. See instructions [here](https://docs.powersync.com/integration-guides/supabase-+-powersync#connect-powersync-to-your-supabase).
3. Deploy Sync Streams.

### Sync Streams Configuration

This demo uses [Sync Streams](https://docs.powersync.com/sync/streams/overview) with auto-subscribed streams to sync data. Deploy the following configuration:

```YAML
config:
  edition: 3

streams:
  all_lists:
    priority: 1
    auto_subscribe: true
    query: SELECT * FROM lists WHERE owner_id = auth.user_id()
    

  list_items:
    auto_subscribe: true
    query: SELECT todos.* FROM todos INNER JOIN lists ON todos.list_id = lists.id WHERE lists.owner_id = auth.user_id()
```

Giving lists a [higher priority](https://docs.powersync.com/sync/advanced/prioritized-sync) allows updates to be synced before all items have been received.

## Configure project in Android Studio

1. Clone this repo: ```git clone https://github.com/powersync-ja/powersync-kotlin.git```
2. Open the repo in Android Studio. This creates a `local.properties` file in root and should contain a `sdk.dir=/path/to/android/sdk` line.
3. Sync the project with Gradle (this should happen automatically, or choose File > Sync project with Gradle Files).
4. Insert your Supabase project URL, Supabase Anon Key, and PowerSync instance URL into the `demos/supabase-todolist/local.properties` file:

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
