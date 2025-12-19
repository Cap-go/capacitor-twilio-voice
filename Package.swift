// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorTwilioVoice",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoCapacitorTwilioVoice",
            targets: ["CapacitorTwilioVoicePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/twilio/twilio-voice-ios", from: "6.13.5")
    ],
    targets: [
        .target(
            name: "CapacitorTwilioVoicePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "TwilioVoice", package: "twilio-voice-ios")
            ],
            path: "ios/Sources/CapacitorTwilioVoicePlugin",
            linkerSettings: [
                .linkedFramework("PushKit"),
                .linkedFramework("CallKit"),
                .linkedFramework("AVFoundation")
            ]),
        .testTarget(
            name: "CapacitorTwilioVoicePluginTests",
            dependencies: ["CapacitorTwilioVoicePlugin"],
            path: "ios/Tests/CapacitorTwilioVoicePluginTests")
    ]
)
