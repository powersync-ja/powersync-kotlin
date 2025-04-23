package com.powersync

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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

    val path = Files.createTempFile(Path(System.getProperty("java.io.tmpdir")), "$prefix$fileName", extension)
    val file =
        path.toFile().apply {
            setReadable(true)
            setWritable(true)
            setExecutable(true)

            deleteOnExit()
        }

    val resourcePath = "/$prefix${fileName}_$arch.$extension"

    (R::class.java.getResourceAsStream(resourcePath) ?: error("Resource $path not found")).use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }

    return path.absolutePathString()
}
