package com.powersync.plugins.sonatype

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import java.net.URI

internal inline val Project.gradlePublishing: PublishingExtension
    get() = extensions.getByType(PublishingExtension::class.java)

internal fun Project.findOptionalProperty(propertyName: String) = findProperty(propertyName)?.toString()


/** Sets up repository for publishing to Github Packages to GITHUB_REPO property
 * username and password (a personal Github access token) should be specified as
 * `GITHUB_PUBLISH_USER` and `GITHUB_PUBLISH_TOKEN` gradle properties
 */
@Suppress("unused")
public fun Project.setupGithubRepository() {
    gradlePublishing.apply {
        val githubRepo = githubRepoOrNull ?: return

        val githubPublishToken =
            githubPublishTokenOrNull ?: return
        val githubPublishUser = project.githubPublishUser ?: "cirunner"

        repositories.maven {
            it.name = "githubPackages"
            it.url = URI.create("https://maven.pkg.github.com/$githubRepo")
            it.credentials { cred ->
                cred.username = githubPublishUser
                cred.password = githubPublishToken
            }
        }
    }
}

internal val Project.githubPublishUser: String?
    get() = project.findOptionalProperty("GITHUB_PUBLISH_USER")

internal val Project.githubPublishTokenOrNull: String?
    get() = project.findOptionalProperty("GITHUB_PUBLISH_TOKEN")

internal val Project.githubRepoOrNull: String?
    get() {
        val repo = findOptionalProperty("GITHUB_REPO") ?: return null
        val repoWithoutGitSuffix = repo.removeSuffix(".git")
        val regex = Regex("((.*)[/:])?(?<owner>[^:/]+)/(?<repo>[^/]+)")
        val matchResult = regex.matchEntire(repoWithoutGitSuffix)
        if (matchResult != null) {
            return (matchResult.groups["owner"]!!.value + "/" + matchResult.groups["repo"]!!.value)
        } else {
            throw IllegalArgumentException("Incorrect Github repository path, should be \"Owner/Repo\"")
        }
    }