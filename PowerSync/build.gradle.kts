import co.touchlab.faktory.artifactmanager.ArtifactManager
import co.touchlab.faktory.capitalized
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kmmbridge)
    alias(libs.plugins.skie)
    alias(libs.plugins.mavenPublishPlugin)
    id("com.powersync.plugins.sonatype")
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
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

skie {
    features {
        group {
            // We turn this off as the suspend interop feature results in
            // threading issues when implementing SDK in Swift
            SuspendInterop.Enabled(false)
        }
    }
}

kmmbridge {
    artifactManager.set(SonatypePortalPublishArtifactManager(project, null, null, null))
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
// and needs to be in this file as it requires access to import co.touchlab.faktory
// which is only available here
class SonatypePortalPublishArtifactManager(
    val project: Project,
    private val publicationName: String?,
    artifactSuffix: String?,
    private val repositoryName: String?,
) : ArtifactManager {
    private val FRAMEWORK_PUBLICATION_NAME = "KMMBridgeFramework"
    private val KMMBRIDGE_ARTIFACT_SUFFIX = "kmmbridge"
    private val group: String = project.group.toString().replace(".", "/")
    private val kmmbridgeArtifactId =
        "${project.name}-${artifactSuffix ?: KMMBRIDGE_ARTIFACT_SUFFIX}"
    private val LIBRARY_VERSION: String by project
    // This is the URL that will be added to Package.swift in Github package so that
    // KMMBridge is downloaded when a user includes the package in XCode
    private val MAVEN_CENTRAL_PACKAGE_ZIP_URL = "https://repo1.maven.org/maven2/com/powersync/${kmmbridgeArtifactId.lowercase()}/${LIBRARY_VERSION}/${kmmbridgeArtifactId.lowercase()}-${LIBRARY_VERSION}.zip"

    override fun deployArtifact(project: Project, zipFilePath: File, version: String): String {
        return MAVEN_CENTRAL_PACKAGE_ZIP_URL
    }

    override fun configure(
        project: Project,
        version: String,
        uploadTask: TaskProvider<Task>,
        kmmPublishTask: TaskProvider<Task>
    ) {
        project.extensions.getByType<PublishingExtension>().publications.create(
            publicationName ?: FRAMEWORK_PUBLICATION_NAME, MavenPublication::class.java
        ) {
            this.version = version
            val archiveProvider = project.tasks.named("zipXCFramework", Zip::class.java).flatMap {
                it.archiveFile
            }
            artifact(archiveProvider) {
                extension = "zip"
            }
            artifactId = kmmbridgeArtifactId
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
            publishingExtension.publications.getByName(
                publicationName ?: FRAMEWORK_PUBLICATION_NAME
            ).name.capitalized()

        return publishingExtension.repositories.filterIsInstance<MavenArtifactRepository>()
            .map { repo ->
                val repositoryName = repo.name.capitalized()
                val publishTaskName =
                    "publish${publicationNameCap}PublicationTo${repositoryName}Repository"
                // Verify that the "publish" task exists before collecting
                project.tasks.named(publishTaskName)
            }
    }
}