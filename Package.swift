// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://repo1.maven.org/maven2/com/powersync/powersync-kmmbridge/1.0.0-BETA25/powersync-kmmbridge-1.0.0-BETA25.zip"
let remoteKotlinChecksum = "3bc997b48f74497b4f9adf37a1c5e8854726ca821b5d91f970f509c57fa4b90b"
let packageName = "PowerSyncKotlin"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
        ,
    ]
)