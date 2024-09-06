// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://repo1.maven.org/maven2/com/powersync/powersync-kmmbridge/1.0.0-BETA1/powersync-kmmbridge-1.0.0-BETA1.zip"
let remoteKotlinChecksum = "83e2c6e65e48e6d1d2a1c43d0f8d398d9191134a90125a86a81cce1db0e76670"
let packageName = "PowerSync"
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