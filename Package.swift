// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://repo1.maven.org/maven2/com/powersync/powersync-kmmbridge/1.0.0/powersync-kmmbridge-1.0.0.zip"
let remoteKotlinChecksum = "0a9a7dc2984c458f48107dbb60024a2043d5e3f696c54d9307bcac5b694b2b07"
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