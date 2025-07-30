// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorTwilioVoice",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapgoCapacitorTwilioVoice",
            targets: ["CapacitorTwilioVoicePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "CapacitorTwilioVoicePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CapacitorTwilioVoicePlugin"),
        .testTarget(
            name: "CapacitorTwilioVoicePluginTests",
            dependencies: ["CapacitorTwilioVoicePlugin"],
            path: "ios/Tests/CapacitorTwilioVoicePluginTests")
    ]
)
