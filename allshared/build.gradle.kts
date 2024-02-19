plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kmmbridge)
    alias(libs.plugins.skie)
    alias(libs.plugins.mavenPublishPlugin)
}

kotlin {

    listOf(
//        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            export(project(":core"))
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = project.uri("https://maven.pkg.github.com/powersync-ja/powersync-kotlin-sdk")
            credentials(PasswordCredentials::class)
        }
    }
}

kmmbridge {
    mavenPublishArtifacts()
    spm()
}
