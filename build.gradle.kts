import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files

plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.mavenPublishPlugin) apply false
    alias(libs.plugins.downloadPlugin) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
    alias(libs.plugins.cocoapods) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    id("org.jetbrains.dokka") version libs.versions.dokkaBase
    id("dokka-convention")
}

tasks.getByName<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Merges individual module docs into a single HTML output
dependencies {
    dokka(project(":common:"))
    dokka(project(":core:"))
    dokka(project(":compose:"))
    dokka(project(":integrations:room"))
    dokka(project(":integrations:sqldelight"))
    dokka(project(":integrations:supabase"))
    dokka(projects.sqlite3multipleciphers)
}

dokka {
    moduleName.set("PowerSync Kotlin")
}

develocity {
    val isPowerSyncCI = System.getenv("GITHUB_REPOSITORY") == "powersync-ja/powersync-kotlin"

    buildScan {
        // We can't know if everyone running this build has accepted the TOS, but we've accepted
        // them for our CI.
        if (isPowerSyncCI) {
            termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
            termsOfUseAgree.set("yes")
        }

        // Only upload build scan if the --scan parameter is set
        publishing.onlyIf { false }
    }
}

// Serve the generated Dokka documentation using a simple HTTP server
// File changes are not watched here
tasks.register("serveDokka") {
    group = "dokka"
    dependsOn("dokkaGenerate")
    val rootProvider = layout.buildDirectory.dir("dokka/html")

    doLast {
        val root = rootProvider.get().asFile
        val server = HttpServer.create(InetSocketAddress(0), 0)

        val handler =
            com.sun.net.httpserver.HttpHandler { exchange: HttpExchange ->
                val rawPath = exchange.requestURI.path
                val cleanPath = URLDecoder.decode(rawPath.removePrefix("/"), "UTF-8")
                val requestedFile = File(root, cleanPath)

                val file =
                    when {
                        requestedFile.exists() && !requestedFile.isDirectory -> requestedFile
                        else -> File(root, "index.html") // fallback
                    }

                val contentType =
                    Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                val bytes = file.readBytes()
                exchange.responseHeaders.add("Content-Type", contentType)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

        server.createContext("/", handler)
        server.executor = null
        server.start()

        println("📘 Serving Dokka docs at http://localhost:${server.address.port}/")
        println("Press Ctrl+C to stop.")

        // Keep the task alive
        Thread.currentThread().join()
    }
}
