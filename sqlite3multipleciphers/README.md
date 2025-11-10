## Building

Please note that the build currently only runs on macOS.
We use cross-compilation to be able to compile for Windows and Linux.

For Linux, we use a Docker container to build the extension. To run the build,
first build that image:

```shell
docker build -t powersync_kotlin_sqlite3mc_build_helper --load src/jni
```

To compile for Windows, we use [llvm-mingw](https://github.com/mstorsjo/llvm-mingw),
which needs to be downloaded.

With all dependencies ready, run the Gradle task:

```shell
./gradlew sqlite3multipleciphers:jniCompile -PllvmMingw='.../Downloads/llvm-mingw-20251104-ucrt-macos-universal'
```

This outputs binaries to `build/jni-build/`.
