// swift-tools-version:5.3
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/powersync-ja/powersync-kotlin/com/powersync/powersyncswift-kmmbridge/0.0.1-ALPHA12.1715068620528/powersyncswift-kmmbridge-0.0.1-ALPHA12.1715068620528.zip"
let remoteKotlinChecksum = "8ff093d64aa435627f36a0fa04272476f46fbe6e016ec6fb1f5c593c3c31150c"
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