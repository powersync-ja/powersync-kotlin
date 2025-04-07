package com.powersync

import java.io.File

private class R

internal fun extractLib(fileName: String): String {
    val os = System.getProperty("os.name").lowercase()
    val (prefix, extension) =
        when {
            os.contains("nux") || os.contains("nix") || os.contains("aix") -> "lib" to "so"
            os.contains("mac") -> "lib" to "dylib"
            os.contains("win") -> "" to "dll"
            else -> error("Unsupported OS: $os")
        }

    val arch =
        when (val sysArch = System.getProperty("os.arch")) {
            "aarch64" -> "aarch64"
            "amd64", "x86_64" -> "x64"
            else -> error("Unsupported architecture: $sysArch")
        }

    val path = "/$prefix${fileName}_$arch.$extension"

    val resourceURI =
        (R::class.java.getResource(path) ?: error("Resource $path not found"))

    // Wrapping the above in a File handle resolves the URI to a path usable by SQLite.
    // This is particularly relevant on Windows.
    // On Windows [resourceURI.path] starts with a `/`, e.g. `/c:/...`. SQLite does not load this path correctly.
    // The wrapping here transforms the path to `c:/...` which does load correctly.
    return File(resourceURI.path).path.toString()
}
