# PowerSync + Supabase Android Demo: Todo List App

This is a simple to-do list application demonstrating the use of the Kotlin Multiplatform SDK's Android package in an Android Kotlin project with [Supabase](https://supabase.com/).

## Set up your Supabase and PowerSync project

To run this demo, you need Supabase and PowerSync projects. Detailed instructions for integrating PowerSync with Supabase can be found in [the integration guide](https://docs.powersync.com/integration-guides/supabase).

Follow this guide to:
1. Create and configure a Supabase project.
2. Create a new PowerSync instance, connecting to the database of the Supabase project. See instructions [here](https://docs.powersync.com/integration-guides/supabase-+-powersync#connect-powersync-to-your-supabase).
3. Deploy sync rules.

## Configure project in Android Studio

1. Clone this repo: ```git clone https://github.com/powersync-ja/powersync-kotlin.git```
2. Open `powersync-kotlin/demos/android-supabase-todolist` in Android Studio.
3. Sync the project with Gradle (this should happen automatically, or choose File > Sync project with Gradle Files).
4. Insert your Supabase project URL, Supabase Anon Key, and PowerSync instance URL into the `local.properties` file:

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

Choose a run configuration for the Android app in Android Studio and run it.
