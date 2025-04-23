package com.powersync

import java.io.File
import java.util.UUID

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

    val suffix = UUID.randomUUID().toString()
    val file = File(System.getProperty("java.io.tmpdir"), "$prefix$fileName-$suffix.$extension").apply {
        setReadable(true)
        setWritable(true)
        setExecutable(true)

        deleteOnExit()
    }


    val resourcePath = "/$prefix${fileName}_$arch.$extension"

    (R::class.java.getResourceAsStream(resourcePath) ?: error("Resource $resourcePath not found")).use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }

    println("PowerSync loadable should be at $file")
    return file.absolutePath
}
