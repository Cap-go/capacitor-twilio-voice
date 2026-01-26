// swift-tools-version: 5.9
import PackageDescription

// DO NOT MODIFY THIS FILE - managed by Capacitor CLI commands
let package = Package(
    name: "CapApp-SPM",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapApp-SPM",
            targets: ["CapApp-SPM"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.1"),
        .package(name: "CapacitorLocalNotifications", path: "../../../node_modules/.bun/@capacitor+local-notifications@7.0.3+15e98482558ccfe6/node_modules/@capacitor/local-notifications"),
        .package(name: "CapacitorPushNotifications", path: "../../../node_modules/.bun/@capacitor+push-notifications@7.0.3+15e98482558ccfe6/node_modules/@capacitor/push-notifications"),
        .package(name: "CapgoCapacitorTwilioVoice", path: "../../../node_modules/.bun/@capgo+capacitor-twilio-voice@file+..+8ad980643f635cc1/node_modules/@capgo/capacitor-twilio-voice"),
        .package(name: "CapgoNativeAudio", path: "../../../node_modules/.bun/@capgo+native-audio@7.6.1+15e98482558ccfe6/node_modules/@capgo/native-audio")
    ],
    targets: [
        .target(
            name: "CapApp-SPM",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "CapacitorLocalNotifications", package: "CapacitorLocalNotifications"),
                .product(name: "CapacitorPushNotifications", package: "CapacitorPushNotifications"),
                .product(name: "CapgoCapacitorTwilioVoice", package: "CapgoCapacitorTwilioVoice"),
                .product(name: "CapgoNativeAudio", package: "CapgoNativeAudio")
            ]
        )
    ]
)
