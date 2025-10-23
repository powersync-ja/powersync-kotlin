# PowerSync common

This module contains core definitions for the PowerSync SDK, without linking or bundling a SQLite dependency.

This allows the module to be used as a building block for PowerSync SDKs with and without encryption support.

Users should typically depend on `:core` instead.

## Structure

This is a Kotlin Multiplatform project targeting Android, iOS platforms, with the following
structure:

- `commonMain` - Shared code for all targets, which includes the `PowerSyncBackendConnector`
  interface and `PowerSyncBuilder` for building a `PowerSync` instance. It also defines
  the `DatabaseDriverFactory` class to be implemented in each platform.
- `commonJava` - Shared logic for Android and Java targets.
- `androidMain` - Android-specific code for loading the core extension.
- `jvmMain` - Java-specific code for loading the core extension.
- `nativeMain` - A SQLite driver implemented with cinterop calls to sqlite3.
- `appleMain`: Utilities for finding a suitable database location on Apple platforms.
- `appleNonWatchOsMain` and `watchosMain`: Loads the PowerSync core extension (which is linked statically on watchOS
   and dynamically on other platforms).

## Attachment Helpers

This module contains attachment helpers under the `com.powersync.attachments` package. See
the [Attachment Helpers README](../common/src/commonMain/kotlin/com/powersync/attachments/README.md)
