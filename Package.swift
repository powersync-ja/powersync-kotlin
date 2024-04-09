// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/powersyncswift-kmmbridge/0.0.1-ALPHA7.1712649684976/powersyncswift-kmmbridge-0.0.1-ALPHA7.1712649684976.zip"
let remoteKotlinChecksum = "c705336bd133caf397ee6e99387a6cb8bf890853a5efd17a432577175b384f33"
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