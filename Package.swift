// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/PowerSync-kmmbridge/0.0.1-ALPHA14.1718917766382/PowerSync-kmmbridge-0.0.1-ALPHA14.1718917766382.zip"
let remoteKotlinChecksum = "8cfd354db5d36f7149d457ee33064b1e6b57d44b396afdff0f014746119b7e4b"
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