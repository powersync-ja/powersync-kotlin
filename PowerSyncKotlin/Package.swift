// swift-tools-version: 5.7

// NOTE! This is never released, we're only using this to support local Kotlin SDK builds for the
// Swift SDK.
import PackageDescription
let packageName = "PowerSyncKotlin"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            path: "build/XCFrameworks/debug/PowerSyncKotlin.xcframework"
        )
    ]
)
