plugins {
    id("org.jetbrains.dokka")
}

// Shared Dokka config for additional assets
dokka {
    pluginsConfiguration.html {
        val docsAssetsDir = rootProject.file("docs/assets")

        customAssets.from(docsAssetsDir.resolve("powersync-logo.png"))
        customAssets.from(docsAssetsDir.resolve("logo-icon.svg"))
        customAssets.from(docsAssetsDir.resolve("discord.svg"))
        customAssets.from(docsAssetsDir.resolve("github.svg"))
        customAssets.from(docsAssetsDir.resolve("web.svg"))
        customAssets.from(docsAssetsDir.resolve("x.svg"))
        customAssets.from(docsAssetsDir.resolve("youtube.svg"))
        customAssets.from(docsAssetsDir.resolve("linkedin.svg"))
        customStyleSheets.from(docsAssetsDir.resolve("doc-styles.css"))
        templatesDir = file(docsAssetsDir.resolve("dokka-templates"))
    }
}
