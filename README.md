<p align="center">
  <a href="https://www.powersync.com" target="_blank"><img src="https://github.com/powersync-ja/.github/assets/19345049/602bafa0-41ce-4cee-a432-56848c278722"/></a>
</p>

PowerSync is a service and set of SDKs that keeps Postgres databases in sync with on-device SQLite
databases.

# PowerSync Kotlin Multiplatform SDKs

## Structure: Packages

- [core](./core/README.md)

    - Kotlin Multiplatform SDK implementation

- [connectors](./connectors/README.md)

    - Supabase connector

## Demo Apps / Example Projects

Demo applications are located in the [`demos/`](./demos) directory.

- [demos/hello-powersync](./demos/hello-powersync/README.md): A minimal example demonstrating the
  use of the PowerSync Kotlin Multiplatform SDK.

- [demos/supabase-todolist](./demos/supabase-todolist/README.md): A simple to-do list application
  that uses the PowerSync Kotlin Multiplatform SDK and the Supabase connector.

## Getting Started

### Installation

### Usage

1. Define your schema
2. Implement a `PowerSyncBackendConnector` to connect to your backend
3. Initialize and connect a PowerSync database with your connector

## Development

### Build

### Publishing