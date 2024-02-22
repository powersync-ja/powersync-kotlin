# Hello-PowerSync Demo App

This is a minimal demo app demonstrating use of the PowerSync Kotlin Mutiplatform SDK together
with [Supabase](https://supabase.com/) in a basic Kotlin Multiplatform Compose App. 

The app lists customers and allows you to add or delete rows.
Data is [synced to users globally](https://docs.powersync.com/usage/sync-rules/example-global-data). For more advanced sync rules and functionality, see the [PowerSync+Supabase Todo-List](../supabase-todolist/README.md) demo application.

Supported KMP targets: Android and iOS.

## Alpha release

The Kotlin Multiplatform SDK is currently in an alpha release. If you find a bug or issue, please
open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin/issues). Questions or
feedback can be posted on our [community Discord](https://discord.gg/powersync) - we'd love to hear
from you.

## Set up Supabase project and auth user
1. Create a new Supabase project, and run the below in the Supabase SQL editor. This creates a `customers` table and a publication called `powersync` for the `customers` table.

```sql
-- Create tables
create table
  public.customers (
    id uuid not null default gen_random_uuid (),
    name text not null,
    email text not null,
    constraint customers_pkey primary key (id)
  ) tablespace pg_default;


drop publication powersync;

-- Create publication for powersync
create publication powersync for table customers;

```

2. Create a user which this demo app will use for authentication. This is done under *Authentication* > *Users* > *Add user*. Enter an email address and password for the user.

## Set up PowerSync instance
1. In your [PowerSync dashboard](https://powersync.journeyapps.com/), create a new PowerSync instance, connecting to the database of the Supabase project. Find detailed instructions in the [Connect PowerSync to Your Supabase](https://docs.powersync.com/integration-guides/supabase-+-powersync#connect-powersync-to-your-supabase) section of the Supabase<>PowerSync integration guide.

2. Then deploy the sync rules to the newly created instance.
  - Open the `sync-rules.yaml` file.
  - Replace the file's contents with the below:

    ```yml
    # sync-rules.yaml

    bucket_definitions:
      global:
        data:
          # Sync all rows
          - SELECT * FROM public.customers
    ```
  - Deploy to the newly created instance.

## Set up your development environment
To setup your environment, please consult [these instructions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html).

## Configure project in Android Studio

1. Clone this repo: ```git clone https://github.com/powersync-ja/powersync-kotlin.git```
2. Open the repo in Android Studio. This creates a `local.properties` file in root and should contain a `sdk.dir=/path/to/android/sdk` line.
3. Sync the project with Gradle (this should happen automatically, or choose File > Sync project with Gradle Files).
4. Open the `demos/hello-powersync` directory in Android Studio and sync this project with Gradle.
5. Insert your Supabase project, auth user, and PowerSync project credentials into the `local.properties` file:

```
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

## Run the App
Choose a run configuration for the Android or iOS target in Android Studio and run it.


## Project structure

[//]: # (TODO)

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - `commonMain` is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the
      folder name.
      `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for
  your project.