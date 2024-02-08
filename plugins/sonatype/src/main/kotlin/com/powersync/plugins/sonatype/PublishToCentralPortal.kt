package com.powersync.plugins.sonatype

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Objects

abstract class PublishToCentralPortal() : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val username: Property<String?>

    @get:Input
    @get:Optional
    abstract val password: Property<String?>

    private var archive: AbstractArchiveTask? = null

    fun upload(archive: AbstractArchiveTask) {
        if(this.archive != null) {
            throw IllegalStateException("Archive hsa already set")
        }
        dependsOn(archive)
        this.archive = archive
    }

    open fun outputFile(): File {
        if (archive == null) {
            throw IllegalStateException("Archive has not set")
        }
        return archive!!.archiveFile.get().asFile
    }

    companion object {
        const val UPLOAD_ENDPOINT =
            "https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED"
        const val SUCCESS_STATUS_CODE = 201
    }

    @TaskAction
    @Throws(IOException::class, URISyntaxException::class)
    fun sendRequest() {

        val extension = project.extensions.getByType(SonatypeCentralExtension::class.java)

        val username = this.username.getOrNull() ?: extension.username.getOrNull() ?: Objects.toString(
            project.findProperty("centralPortal.username"),
            null
        )
        ?: throw IOException(
            "Missing PublishToCentralPortal's `username` and `centralPortal.username` value and `centralPortal.username` property"
        )

        val password = this.password.getOrNull() ?: extension.password.getOrNull() ?: Objects.toString(
            project.findProperty("centralPortal.password"),
            null
        )
        ?: throw IOException(
            "Missing PublishToCentralPortal's `password` and centralPortal.password` value and `centralPortal.password` property"
        )

        val outputFile = this.outputFile();

        val name = URLEncoder.encode(
            (project.group
                .toString() + ":" + project.name
                    + ":" + project.version),
            StandardCharsets.UTF_8
        )
        val userPass = "$username:$password"
        val token = Base64.getEncoder().encodeToString(userPass.toByteArray())
        val conn = URI(UPLOAD_ENDPOINT + "&name=" + name)
            .toURL()
            .openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "UserToken $token")
        val boundary = "---------------------------" + java.lang.Long.toHexString(
            System.currentTimeMillis().inv()
        )
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.outputStream.use { out ->
            out.write(("--" + boundary).toByteArray())
            out.write("\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"".toByteArray())
            out.write("\r\nContent-Type: application/octet-stream".toByteArray())
            out.write("\r\n\r\n".toByteArray())
            FileInputStream(outputFile).use { inputStream ->
                val buffer: ByteArray = ByteArray(1024)
                var available: Long = outputFile.length()
                while (available > 0) {
                    val read: Int = inputStream.read(
                        buffer,
                        0,
                        Math.min(buffer.size.toLong(), available).toInt()
                    )
                    out.write(buffer, 0, read)
                    available -= read.toLong()
                }
            }
            out.write(("\r\n--" + boundary + "--\r\n").toByteArray())
            out.flush()
        }
        val status = conn.responseCode
        when (status) {
            SUCCESS_STATUS_CODE -> {}
            else -> throw IOException(
                "Error " + status + ": " + String(conn.errorStream.readAllBytes())
            )
        }
    }

}

