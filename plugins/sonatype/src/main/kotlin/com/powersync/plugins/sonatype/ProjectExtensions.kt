package com.powersync.plugins.sonatype

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import java.net.URI

inline val Project.gradlePublishing: PublishingExtension
    get() = extensions.getByType(PublishingExtension::class.java)

fun Project.findOptionalProperty(propertyName: String) = findProperty(propertyName)?.toString()

/** Sets up repository for publishing to Github Packages
 * username and password (a personal Github access token) should be specified as `githubPackagesUsername` and `githubPackagesPassword` Gradle properties or
 * alternatively as `ORG_GRADLE_PROJECT_githubPackagesUsername` and `ORG_GRADLE_PROJECT_githubPackagesPassword` environment variables
 */
@Suppress("unused")
fun Project.setupGithubRepository() {
    gradlePublishing.apply {
        val githubRepo = githubRepoOrNull ?: ""
        if (githubRepo.isEmpty()) {
            logger.error("GITHUB_REPO property missing")
            return
        }

        repositories.maven {
            it.name = "githubPackages"
            it.url = URI.create("https://maven.pkg.github.com/$githubRepo")
            it.credentials(PasswordCredentials::class.java)
        }
    }
}

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