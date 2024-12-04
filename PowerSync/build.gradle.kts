import co.touchlab.faktory.artifactmanager.ArtifactManager
import co.touchlab.faktory.capitalized
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kmmbridge)
    alias(libs.plugins.skie)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            export(project(":core"))
            isStatic = true
        }
    }

    explicitApi()

    targets.withType<KotlinNativeTarget> {
        compilations.getByName("main") {
            compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}

kmmbridge {
    artifactManager.set(SonatypePortalPublishArtifactManager(project, repositoryName = null))
    artifactManager.finalizeValue()
    spm()
}

// We need this so that when a user includes the package in XCode they are able to
// import the package using Github
if (System.getenv().containsKey("CI")) {
    // Setup github publishing based on GitHub action variables
    addGithubPackagesRepository()
}

// This is required for KMMBridge zip to be uploaded to Sonatype (Maven Central)
// Since this will only ever be used in this build file it does not make sense to make a
// plugin to use this.
class SonatypePortalPublishArtifactManager(
    val project: Project,
    private val publicationName: String = "KMMBridgeFramework",
    artifactSuffix: String = "kmmbridge",
    private val repositoryName: String?
) : ArtifactManager {
    private val group: String = project.group.toString().replace(".", "/")
    private val kmmbridgeArtifactId =
        "${project.name}-$artifactSuffix"
    private val LIBRARY_VERSION: String by project

    // This is the URL that will be added to Package.swift in Github package so that
    // KMMBridge is downloaded when a user includes the package in XCode
    private val MAVEN_CENTRAL_PACKAGE_ZIP_URL = "https://repo1.maven.org/maven2/com/powersync/${kmmbridgeArtifactId.lowercase()}/${LIBRARY_VERSION}/${kmmbridgeArtifactId.lowercase()}-${LIBRARY_VERSION}.zip"

    override fun deployArtifact(
        project: Project,
        zipFilePath: File,
        version: String
    ): String = MAVEN_CENTRAL_PACKAGE_ZIP_URL

    override fun configure(
        project: Project,
        version: String,
        uploadTask: TaskProvider<Task>,
        kmmPublishTask: TaskProvider<Task>
    ) {
        project.extensions.getByType<PublishingExtension>().publications.create(
            publicationName,
            MavenPublication::class.java,
        ) {
            this.version = version
            val archiveProvider =
                project.tasks.named("zipXCFramework", Zip::class.java).flatMap {
                    it.archiveFile
                }
            artifact(archiveProvider) {
                extension = "zip"
            }
            artifactId = kmmbridgeArtifactId
        }

        // Register the task
        project.tasks.register<UpdatePackageSwiftChecksumTask>("updatePackageSwiftChecksum") {
            artifactId.set(kmmbridgeArtifactId)
            zipUrl.set(MAVEN_CENTRAL_PACKAGE_ZIP_URL)
            dependsOn("updatePackageSwift")
        }

        // Make sure this task runs after updatePackageSwift
        project.tasks.named("kmmBridgePublish") {
            dependsOn("updatePackageSwiftChecksum")
        }

        publishingTasks().forEach {
            uploadTask.configure {
                dependsOn(it)
            }
        }
        try {
            project.tasks.named("publish").also { task ->
                task.configure {
                    dependsOn(kmmPublishTask)
                }
            }
        } catch (_: UnknownTaskException) {
            project.logger.warn("Gradle publish task not found")
        }
    }

    private fun publishingTasks(): List<TaskProvider<Task>> {
        val publishingExtension = project.extensions.getByType<PublishingExtension>()

        // Either the user has supplied a correct name, or we use the default. If neither is found, fail.
        val publicationNameCap =
            publishingExtension.publications
                .getByName(
                    publicationName,
                ).name
                .capitalized()

        return publishingExtension.repositories
            .filterIsInstance<MavenArtifactRepository>()
            .map { repo ->
                val repositoryName = repo.name.capitalized()
                val publishTaskName =
                    "publish${publicationNameCap}PublicationTo${repositoryName}Repository"
                // Verify that the "publish" task exists before collecting
                project.tasks.named(publishTaskName)
            }
    }
}

// This task is used to update Package.swift with the checksum of the zip file
// located on maven central.
abstract class UpdatePackageSwiftChecksumTask : DefaultTask() {
    @get:Input
    abstract val artifactId: Property<String>

    @get:Input
    abstract val zipUrl: Property<String>

    @TaskAction
    fun updateChecksum() {
        val LIBRARY_VERSION: String by project

        val zipFile = project.file("${project.layout.buildDirectory.get()}/tmp/${artifactId.get().lowercase()}-$LIBRARY_VERSION.zip")

        // Download the zip file
        zipFile.parentFile.mkdirs()
        URL(zipUrl.get()).openStream().use { input ->
            zipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Compute the checksum
        val checksum =
            zipFile.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = input.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }

        // Update Package.swift
        val packageSwiftFile = project.rootProject.file("Package.swift")
        val updatedContent =
            packageSwiftFile.readText().replace(
                Regex("let remoteKotlinChecksum = \"[a-f0-9]+\""),
                "let remoteKotlinChecksum = \"$checksum\"",
            )
        packageSwiftFile.writeText(updatedContent)

        println("Updated Package.swift with new checksum: $checksum")
    }
}
