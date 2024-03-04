// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/powersyncswift-kmmbridge/0.0.1-ALPHA2.1709546402639/powersyncswift-kmmbridge-0.0.1-ALPHA2.1709546402639.zip"
let remoteKotlinChecksum = "713c4d3da38b8a946d22c8d594b1bcb8b9b0917832c46127664fd1af4612087d"
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