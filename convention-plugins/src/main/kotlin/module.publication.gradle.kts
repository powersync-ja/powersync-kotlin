import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`

plugins {
    `maven-publish`
    signing
}

publishing {
    // Configure all publications
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
        artifact(tasks.register("${name}JavadocJar", Jar::class) {
            archiveClassifier.set("javadoc")
            archiveAppendix.set(this@withType.name)
        })

        // Provide artifacts information required by Maven Central
        pom {
            name.set("PowerSync Kotlin Multiplatform SDK")
            description.set("PowerSync KMP SDK")
            url.set("https://github.com/powersync-ja/powersync-kotlin-sdk")

            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("PowerSync")
                    name.set("PowerSync Team")
                    organization.set("PowerSync")
                    organizationUrl.set("https://powersync.com/")
                }
            }
            scm {
                url.set("https://github.com/powersync-ja/powersync-kotlin-sdk")
            }
        }
    }
}

signing {
    if (project.hasProperty("signing.gnupg.keyName")) {
        useGpgCmd()
        sign(publishing.publications)
    }
}
