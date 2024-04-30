// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/powersyncswift-kmmbridge/0.0.1-ALPHA10.1714470458951/powersyncswift-kmmbridge-0.0.1-ALPHA10.1714470458951.zip"
let remoteKotlinChecksum = "545ec69e8ee509dd559160f830482afae5b3147587c8d3f7b306c0e7b6fd6de6"
let packageName = "powersyncswift"
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