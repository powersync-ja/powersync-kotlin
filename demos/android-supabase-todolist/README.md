# PowerSync + Supabase Android Demo: Todo List App

It is a simple to-do list application demonstrating use of the uses the Kotlin Multiplatform SDK's Android package in an Android Kotlin project with [Supabase](https://supabase.com/).

## Set up Supabase project

A step-by-step guide on Supabase<>PowerSync integration is available [here](https://docs.powersync.com/integration-guides/supabase).

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
