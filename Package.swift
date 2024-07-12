// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/PowerSync-kmmbridge/0.0.1-ALPHA15.1720798811476/PowerSync-kmmbridge-0.0.1-ALPHA15.1720798811476.zip"
let remoteKotlinChecksum = "83b0dc7897ec2ff85b9dd5e2e5c680b68630079d1709ed9996f4777b9adc6614"
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