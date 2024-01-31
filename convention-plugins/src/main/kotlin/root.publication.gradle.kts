import org.gradle.api.tasks.bundling.Jar
import java.util.*

plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.secretKeyRingFile"] = null
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["sonatypeUsername"] = null
ext["sonatypePassword"] = null

// Grabbing secrets from local.properties file or from environment variables, which could be used on CI
val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.secretKeyRingFile"] = System.getenv("OSSRH_GPG_SECRET_KEY")
    ext["signing.keyId"] = System.getenv("OSSRH_GPG_SECRET_KEY_ID")
    ext["signing.password"] = System.getenv("OSSRH_GPG_SECRET_KEY_PASSWORD")
    ext["sonatypeUsername"] = System.getenv("OSSRH_USERNAME")
    ext["sonatypePassword"] = System.getenv("OSSRH_PASSWORD")
    ext["sonatypeStagingProfileId"] = System.getenv("OSSRH_PROFILE_ID")
}

fun getExtraString(name: String) = ext[name]?.toString()


nexusPublishing {
    useStaging = true
    // Configure maven central repository
    // https://github.com/gradle-nexus/publish-plugin#publishing-to-maven-central-via-sonatype-ossrh
    repositories {
        sonatype {
            username.set(getExtraString("sonatypeUsername"))
            password.set(getExtraString("sonatypePassword"))
            stagingProfileId.set(getExtraString("sonatypeStagingProfileId"))

            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
