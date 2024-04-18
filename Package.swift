// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/powersyncswift-kmmbridge/0.0.1-ALPHA9.1713454378167/powersyncswift-kmmbridge-0.0.1-ALPHA9.1713454378167.zip"
let remoteKotlinChecksum = "43bccb9ff3a5b143582d22e55186f45605c56b8823b2e5b6313bc1559be34758"
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