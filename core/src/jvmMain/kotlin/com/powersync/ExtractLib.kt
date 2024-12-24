package com.powersync

import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

private class R

internal fun extractLib(fileName: String): Path {
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
            "amd64" -> "x64"
            else -> error("Unknown architecture: $sysArch")
        }

    val path = "/$prefix${fileName}_$arch.$extension"

    val tmpPath = createTempFile("$prefix$fileName", ".$extension")
    Runtime.getRuntime().addShutdownHook(Thread { tmpPath.deleteIfExists() })

    (R::class.java.getResourceAsStream(path) ?: error("Resource $path not found")).use { input ->
        tmpPath.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return tmpPath
}
