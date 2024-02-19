<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/19345049/602bafa0-41ce-4cee-a432-56848c278722"/></a>
</p>

[PowerSync](https://powersync.com) is a service and set of SDKs that keeps Postgres databases in sync with on-device SQLite
databases.

# PowerSync Kotlin Multiplatform SDK

## Alpha release

This SDK is currently in an alpha release. If you find a bug or issue, please open a [GitHub issue](https://github.com/powersync-ja/powersync-kotlin-sdk/issues). Questions or feedback can be posted on our [community Discord](https://discord.gg/powersync) - we'd love to hear from you.

## SDK Features
* Connects to the PowerSync service to sync data with any backend Postgres database.
* Instantly-reactive UI interactions for the user.
* Real-time streaming of changes.
* Operations are asynchronous by default — they do not block UI interactions.
* Supports one write and many reads concurrently.
* No need for client-side database migrations — these are handled automatically.

Supported targets: Android and iOS.

## Structure: Packages

- [core](./core/README.md)

    - Kotlin Multiplatform SDK implementation

- [connectors](./connectors/README.md)

    - Supabase connector

## Demo Apps / Example Projects

The easiest way to test the PowerSync KMP SDK is to run one of our demo applications.

Demo applications are located in the [`demos/`](./demos) directory. See their respective README's for testing instructions:

- [demos/hello-powersync](./demos/hello-powersync/README.md): A minimal example demonstrating the
  use of the PowerSync Kotlin Multiplatform SDK. 

- [demos/supabase-todolist](./demos/supabase-todolist/README.md): ** Currently a work in progress** A simple to-do list application
  that uses the PowerSync Kotlin Multiplatform SDK and the Supabase connector. 

## Limitations

TODO

## Getting Started

### Installation

TODO

### Usage

The first step is creating a PowerSync account and setting up a PowerSync instance. If you are using Supabase, we have a step-by-step tutorial available [here](https://docs.powersync.com/integration-guides/supabase-+-powersync). For other Postgres backend providers, follow these steps:
* Sign up for a free PowerSync account here [https://www.powersync.com/](https://www.powersync.com/). 
* Visit the [PowerSync dashboard](https://powersync.journeyapps.com/) to create a PowerSync instance. After signing up you will be prompted to start the onboarding wizard which guides your though the steps required for this, and find database sepcific instructions [here](https://docs.powersync.com/usage/installation/database-setup). Existing users: start the onboarding wizard by navigating to Help > Start guide in the top-right corner.


#### Implement a backend connector and initialize the PowerSync database

1. Define the schema for the local SQLite database.

TODO: code snippet

2. Implement a backend connector to define how PowerSync communicates with your backend.

TODO: code snippet

3. Initialize the PowerSync database.

TODO: code snippet



## Development

### Build

TODO

### Publishing

TODO