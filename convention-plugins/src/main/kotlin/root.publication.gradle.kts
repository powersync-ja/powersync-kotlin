plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

nexusPublishing {
    // Configure maven central repository
    // https://github.com/gradle-nexus/publish-plugin#publishing-to-maven-central-via-sonatype-ossrh
    repositories {
        sonatype {
            val stagingProfile = project.properties["sonatypeStagingProfileId"].toString()
            if(stagingProfile.isNotBlank()) {
                stagingProfileId.set(stagingProfile)
            }

            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
