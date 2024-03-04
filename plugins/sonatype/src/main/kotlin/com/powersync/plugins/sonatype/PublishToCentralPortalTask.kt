package com.powersync.plugins.sonatype

import com.powersync.plugins.sonatype.SonatypeCentralExtension.Companion.COMPONENT_BUNDLE_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
abstract class PublishToCentralPortalTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val username: Property<String?>

    @get:Input
    @get:Optional
    abstract val password: Property<String?>

    private fun outputFile(): File {
        val archive = project.tasks.getByName(COMPONENT_BUNDLE_TASK_NAME) as Zip
        return archive.archiveFile.get().asFile
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

        val username = this.username.getOrNull() ?: extension.username.getOrNull() ?: project.findOptionalProperty(SonatypeCentralExtension.SONATYPE_USERNAME_KEY)
        ?: throw IOException(
            "Missing PublishToCentralPortal's `username` and `${SonatypeCentralExtension.SONATYPE_USERNAME_KEY}` value and `${SonatypeCentralExtension.SONATYPE_USERNAME_KEY}` property"
        )

        val password = this.password.getOrNull() ?: extension.password.getOrNull() ?: project.findOptionalProperty(SonatypeCentralExtension.SONATYPE_PASSWORD_KEY)
        ?: throw IOException(
            "Missing PublishToCentralPortal's `password` and `${SonatypeCentralExtension.SONATYPE_PASSWORD_KEY}` value and `${SonatypeCentralExtension.SONATYPE_PASSWORD_KEY}` property"
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

