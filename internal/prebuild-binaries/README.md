## Prebuild SQLite binaries

The purpose of this internal project is to build variants of SQLite and SQLite3MultipleCiphers used in the PowerSync
Kotlin SDK.

Specifically, this builds:

1. SQLite as a static library for iOS/macOS/watchOS/tvOS (+ simulators).
2. SQLite3MultipleCiphers as a static library for iOS/macOS/watchOS/tvOS (+ simulators).

We don't want to build these assets on every build since they're included in a `cinterops` definition file, meaning that
they would have to be built during Gradle sync, which slows down that process.

Instead, we have a manually-triggered GitHub workflow creating these assets as an artifact. That workflow then
dispatches an upload workflow on the `powersync-ja/kotlin-assets` repository to upload assets as a GitHub release.
Finally, the main build in this repository will download assets from that release.
