## Prebuild SQLite binaries

The purpose of this internal project is to build variants of SQLite and SQLite3MultipleCiphers used in the PowerSync
Kotlin SDK.

Specifically, this builds:

1. SQLite as a static library for iOS/macOS/watchOS/tvOS (+ simulators).
2. SQLite3MultipleCiphers as a static library for iOS/macOS/watchOS/tvOS (+ simulators).
3. SQLite3MultipleCiphers plus JNI wrappers as a dynamic library for Windows, macOS and Linux.

We don't want to build these assets on every build since they're included in a `cinterops` definition file, meaning that
they would have to be built during Gradle sync, which slows down that process.

Instead, we use a cache for GitHub actions to only recompile these when necessary. During the main build, we then use
a custom property to download assets instead of recompiling.

This build is currently configured to run on macOS hosts only. Cross-compiling requires additional dependencies:

1. To target Windows, we use [LLVM-mingw](https://github.com/mstorsjo/llvm-mingw), which can be downloaded with the
   `download_llvm_mingw.sh`.
2. To target Linux, we use clang. The `download_glibc.sh` file downloads necessary glibc headers and object files.
