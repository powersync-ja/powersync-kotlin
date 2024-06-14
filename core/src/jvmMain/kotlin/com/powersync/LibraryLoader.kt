package com.powersync

import com.powersync.LibraryLoader.Companion.SQLITE_BINARY_FILENAME
import java.io.File
import java.io.InputStream

private interface LibraryLoader {
    companion object {
        const val SQLITE_BINARY_FILENAME: String = "libpowersync-sqlite"
    }

    fun loadLibraryFromResources(): String

    fun createTempFile(prefix: String, suffix: String): File {
        val dir = System.getProperty("java.io.tmpdir")
        return File.createTempFile(prefix, suffix, File(dir))
    }
}

// TODO: Need to create and test implementations of this for windows/linux, or even better implement a cleaner
//  of extracting the shared library.
public class OSXLibraryLoader : LibraryLoader {
    override fun loadLibraryFromResources(): String {
        val path = "/$SQLITE_BINARY_FILENAME.dylib"
        val tempFile = createTempFile(SQLITE_BINARY_FILENAME, ".dylib")
        tempFile.deleteOnExit()

        val inputStream: InputStream = LibraryLoader::class.java.getResourceAsStream(path)
            ?: throw IllegalArgumentException("File $path not found in resources")
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile.absolutePath
    }
}