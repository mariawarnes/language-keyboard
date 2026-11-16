// swift-tools-version:5.9
// Keyboard logic shared by the iOS app and keyboard (layouts, Hangul, language names).
// No UIKit, so its tests run on any Mac with `swift test`.
import PackageDescription

let package = Package(
    name: "KeyboardCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "KeyboardCore", targets: ["KeyboardCore"])],
    targets: [
        .target(name: "KeyboardCore"),
        .testTarget(name: "KeyboardCoreTests", dependencies: ["KeyboardCore"]),
    ]
)
