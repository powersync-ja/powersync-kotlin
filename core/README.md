# PowerSync core module

The PowerSync core module provides the core functionality for the PowerSync Kotlin Multiplatform
SDK.

## Structure

This is a Kotlin Multiplatform project targeting Android, iOS platforms, with the following
structure:

- `commonMain` - Shared code for all targets, which includes the `PowerSyncBackendConnector`
  interface and `PowerSyncBuilder` for building a `PowerSync` instance. It also defines
  the `DatabaseDriverFactory` class to be implemented in each platform.
- `androidMain` - Android specific code, which includes an implementation of
  `PersistentConnectionFactory`.
- `jvmMain` - JVM specific code which includes an implementation of `PersistentConnectionFactory`.
- `nativeMain` - iOS specific code, which includes am implementation of `PersistentConnectionFactory`.

## Attachment Helpers

This module contains attachment helpers under the `com.powersync.attachments` package. See
the [Attachment Helpers README](../common/src/commonMain/kotlin/com/powersync/attachments/README.md)
