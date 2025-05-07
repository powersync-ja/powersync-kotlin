# Powersync Kotlin SDK

## How to make a release

1. Update `LIBRARY_VERSION` in `gradle.properties` in the root.
2. Add an entry to the `CHANGELOG.md`.
3. Make a PR and merge it.
4. Pull `main` (which now contains your merged PR) and create a tag matching the version, e.g.
   `git tag v1.1.0`.
5. Push that tag and manually trigger the GitHub action `Release` on that tag. This will:
   - Create a release to Maven Central.
   - Create a draft release on Github.
   - Build and attach an `XCFramework` zip archive for the Swift SDK to the draft release.
6. Copy relevant entries from the changelog into the draft release and make it public!
7. To apply this release to the Swift SDK, update the `Package.swift` file to point at the framework
   from that release. You can copy the SHA256 hashsum from the generated draft release notes.
