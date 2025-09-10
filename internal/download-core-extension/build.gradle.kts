import de.undercouch.gradle.tasks.download.Download

// The purpose of this project is to share downloaded PowerSync artifacts between multiple other
// projects for testing. This avoids downloading them multiple times.
// This pattern has been adopted from https://docs.gradle.org/current/samples/sample_cross_project_output_sharing.html

plugins {
    alias(libs.plugins.downloadPlugin)
}

val downloadPowersyncFramework by tasks.registering(Download::class) {
    val url = libs.versions.powersync.core.map { coreVersion ->
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync-sqlite-core.xcframework.zip"
    }
    val binariesFolder = project.layout.buildDirectory.dir("binaries")

    src(url)
    dest(binariesFolder.map { it.file("framework/powersync-sqlite-core.xcframework.zip") })
    onlyIfModified(true)
}

val unzipPowerSyncFramework by tasks.registering(Exec::class) {
    inputs.files(downloadPowersyncFramework.map { it.outputFiles })

    val zipfile = downloadPowersyncFramework.get().dest
    val destination = File(zipfile.parentFile, "extracted")
    doFirst {
        destination.deleteRecursively()
        destination.mkdir()
    }

    // We're using unzip here because the Gradle copy task doesn't support symlinks.
    executable = "unzip"
    args(zipfile.absolutePath)
    workingDir(destination)
    outputs.dir(destination)
}

val powersyncFrameworkConfiguration by configurations.creating {
    isCanBeResolved = false
}

artifacts {
    add(powersyncFrameworkConfiguration.name, unzipPowerSyncFramework)
}
