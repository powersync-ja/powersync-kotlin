// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://repo1.maven.org/maven2/com/powersync/powersync-kmmbridge/1.0.0-BETA13/powersync-kmmbridge-1.0.0-BETA13.zip"
let remoteKotlinChecksum = "01c0c8e13b903bb297dcaeef26cc4ca6af8e23266f6e6099cb1a0f494794e147"
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