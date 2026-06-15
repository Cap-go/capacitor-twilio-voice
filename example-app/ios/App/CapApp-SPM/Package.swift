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
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", exact: "8.0.0"),
        .package(name: "CapacitorLocalNotifications", path: "../../../node_modules/.bun/@capacitor+local-notifications@8.2.0+15e98482558ccfe6/node_modules/@capacitor/local-notifications"),
        .package(name: "CapacitorPushNotifications", path: "../../../node_modules/.bun/@capacitor+push-notifications@8.1.1+15e98482558ccfe6/node_modules/@capacitor/push-notifications"),
        .package(name: "CapgoCapacitorTwilioVoice", path: "../../../node_modules/.bun/@capgo+capacitor-twilio-voice@file+..+8ad980643f635cc1/node_modules/@capgo/capacitor-twilio-voice"),
        .package(name: "CapgoNativeAudio", path: "../../../node_modules/.bun/@capgo+native-audio@8.4.2+15e98482558ccfe6/node_modules/@capgo/native-audio"),
        .package(name: "CapgoCapacitorUpdater", path: "../../../node_modules/.bun/@capgo+capacitor-updater@8.47.10+15e98482558ccfe6/node_modules/@capgo/capacitor-updater"),
        .package(name: "CapacitorSplashScreen", path: "../../../node_modules/.bun/@capacitor+splash-screen@8.0.1+15e98482558ccfe6/node_modules/@capacitor/splash-screen")
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
                .product(name: "CapgoNativeAudio", package: "CapgoNativeAudio"),
                .product(name: "CapgoCapacitorUpdater", package: "CapgoCapacitorUpdater"),
                .product(name: "CapacitorSplashScreen", package: "CapacitorSplashScreen")
            ]
        )
    ]
)
