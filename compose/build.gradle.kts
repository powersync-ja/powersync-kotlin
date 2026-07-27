import com.powersync.plugins.utils.powersyncTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    id("com.powersync.plugins.sharedbuild")
    id("dokka-convention")
}

kotlin {
    powersyncTargets(
        includeTargetsWithoutComposeSupport = false,
        // Recent versions of Compose Multiplatform generate bytecode with Java 11, which we have
        // to adopt as well
        legacyJavaSupport = false,
        android = {
            namespace = "com.powersync.compose"
        },
        web = true,
    )

    explicitApi()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
            implementation(compose.runtime)
        }
        val commonNonWebMain by creating {
            dependsOn(commonMain.get())
        }

        jvmMain {
            dependsOn(commonNonWebMain)
        }
        androidMain {
            dependsOn(commonNonWebMain)
        }
        nativeMain {
            dependsOn(commonNonWebMain)
        }

        androidMain.dependencies {
            implementation(compose.foundation)
        }
    }
}

dokka {
    moduleName.set("PowerSync Compose")
}
