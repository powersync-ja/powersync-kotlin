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
    alias(libs.plugins.keeper) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
    alias(libs.plugins.cocoapods) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    id("org.jetbrains.dokka") version libs.versions.dokkaBase
    id("dokka-convention")
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        // Repo for the backported Android IntelliJ Plugin by Jetbrains used in Ultimate
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/")
    }

    configurations.configureEach {
        exclude(group = "com.jetbrains.rd")
        exclude(group = "com.github.jetbrains", module = "jetCheck")
        exclude(group = "com.jetbrains.intellij.platform", module = "wsl-impl")
        exclude(group = "org.roaringbitmap")
        exclude(group = "com.jetbrains.infra")
        exclude(group = "org.jetbrains.teamcity")
        exclude(group = "org.roaringbitmap")
        exclude(group = "ai.grazie.spell")
        exclude(group = "ai.grazie.model")
        exclude(group = "ai.grazie.utils")
        exclude(group = "ai.grazie.nlp")

        // We have a transitive dependency on this due to Kermit, but need the fixed version to support Java 8
        resolutionStrategy.force("co.touchlab:stately-collections:${libs.versions.stately.get()}")
    }
}
subprojects {
    val GROUP: String by project
    val LIBRARY_VERSION: String by project

    group = GROUP
    version = LIBRARY_VERSION
}

tasks.getByName<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Merges individual module docs into a single HTML output
dependencies {
    dokka(project(":core:"))
    dokka(project(":connectors:supabase"))
    dokka(project(":compose:"))
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
    doLast {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val root = file("build/dokka/html")

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
