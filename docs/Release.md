# Powersync Kotlin SDK

## How to make a release

1. Update `LIBRARY_VERSION` and `SWIFT_LIBRARY_VERSION` in `gradle.properties` in the root.
2. Add an entry to the `CHANGELOG.md`.
3. Make a PR and merge it.
4. Once the PR is merged and in the `main` branch then manually run the Github action `Deploy to Sonatype`. This will create a release to Maven Central and will also update the version of the `powersync-kotlin` SPM package used in the Swift SDK. If the release contains changes pertaining to the Swift SDK you will need to update the `powersync-kotlin` SPM package version in that repo and make a release there as well.
