// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://repo1.maven.org/maven2/com/powersync/powersync-kmmbridge/1.0.0-BETA6/powersync-kmmbridge-1.0.0-BETA6.zip"
let remoteKotlinChecksum = "2a9a6e604044ab5e4e51ecc3d86e2a7b8e5d774c62b040efd545fcda765f8643"
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