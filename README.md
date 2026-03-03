## Capacitor Twilio Voice Plugin

 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_twilio_voice"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_twilio_voice"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

A Capacitor plugin for integrating Twilio Voice calling functionality into iOS, Android, and Web/Electron applications.

## Documentation

The most complete doc is available here: <https://capgo.app/docs/plugins/twilio-voice/>

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅          |
| v7.\*.\*       | v7.\*.\*                | On demand   |
| v6.\*.\*       | v6.\*.\*                | ❌          |
| v5.\*.\*       | v5.\*.\*                | ❌          |

> **Note:** The major version of this plugin follows the major version of Capacitor. Use the version that matches your Capacitor installation (e.g., plugin v8 for Capacitor 8). Only the latest major version is actively maintained.

## Installation

```bash
npm install @capgo/capacitor-twilio-voice
npx cap sync
```

## iOS Setup

### 1. Install the plugin

```bash
npm install @capgo/capacitor-twilio-voice
npx cap sync
```

### 2. Setup `CustomCapacitorViewController.swift`

Copy the code from `example-app/ios/App/App/CustomCapacitorViewController.swift` to your `ios/App/App/CustomCapacitorViewController.swift` file.

1. Modify `AppDelegate.swift`

Add the following to `AppDelegate.swift`:

```diff
import UIKit
import Capacitor
+ import PushKit
+ import CapgoCapacitorTwilioVoice

@UIApplicationMain
- class AppDelegate: UIResponder, UIApplicationDelegate {
+ class AppDelegate: UIResponder, UIApplicationDelegate, PKPushRegistryDelegate {

    var window: UIWindow?
+     var pushKitEventDelegate: PushKitEventDelegate?
+     var voipRegistry = PKPushRegistry.init(queue: DispatchQueue.main)

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Override point for customization after application launch.
+         
+         /*
+          * Your app must initialize PKPushRegistry with PushKit push type VoIP at the launch time. As mentioned in the
+          * [PushKit guidelines](https://developer.apple.com/documentation/pushkit/supporting_pushkit_notifications_in_your_app),
+          * the system can't deliver push notifications to your app until you create a PKPushRegistry object for
+          * VoIP push type and set the delegate. If your app delays the initialization of PKPushRegistry, your app may receive outdated
+          * PushKit push notifications, and if your app decides not to report the received outdated push notifications to CallKit, iOS may
+          * terminate your app.
+          */
+         initializePushKit()
+         
+         guard let viewController = UIApplication.shared.windows.first?.rootViewController as? CustomCapacitorViewController else {
+             fatalError("Root view controller is not Capacitor view controller")
+         }
+         
+         viewController.passPushKitEventDelegate = { delegate in
+             self.pushKitEventDelegate = delegate
+         }
+         
+         // self.pushKitEventDelegate = viewController
+         
        return true
    }
+     
+     func initializePushKit() {
+         voipRegistry.delegate = self
+         voipRegistry.desiredPushTypes = Set([PKPushType.voIP])
+     }

    // ... (existing lifecycle methods remain the same) ...

+     // MARK: PKPushRegistryDelegate
+     func pushRegistry(_ registry: PKPushRegistry, didUpdate credentials: PKPushCredentials, for type: PKPushType) {
+         NSLog("pushRegistry:didUpdatePushCredentials:forType:")
+         
+         if let delegate = self.pushKitEventDelegate {
+             delegate.credentialsUpdated(credentials: credentials)
+         }
+     }
+     
+     func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
+         NSLog("pushRegistry:didInvalidatePushTokenForType:")
+         
+         if let delegate = self.pushKitEventDelegate {
+             delegate.credentialsInvalidated()
+         }
+     }
+ 
+     /**
+      * Try using the `pushRegistry:didReceiveIncomingPushWithPayload:forType:withCompletionHandler:` method if
+      * your application is targeting iOS 11. According to the docs, this delegate method is deprecated by Apple.
+      */
+     func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType) {
+         NSLog("pushRegistry:didReceiveIncomingPushWithPayload:forType:")
+         
+         if let delegate = self.pushKitEventDelegate {
+             delegate.incomingPushReceived(payload: payload)
+         }
+     }
+ 
+     /**
+      * This delegate method is available on iOS 11 and above. Call the completion handler once the
+      * notification payload is passed to the `TwilioVoiceSDK.handleNotification()` method.
+      */
+     func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
+         NSLog("pushRegistry:didReceiveIncomingPushWithPayload:forType:completion:")
+ 
+         if let delegate = self.pushKitEventDelegate {
+             delegate.incomingPushReceived(payload: payload, completion: completion)
+         }
+         
+         if let version = Float(UIDevice.current.systemVersion), version >= 13.0 {
+             /**
+              * The Voice SDK processes the call notification and returns the call invite synchronously. Report the incoming call to
+              * CallKit and fulfill the completion before exiting this callback method.
+              */
+             completion()
+         }
+     }

}
```

### 3. Edit `Main.storyboard`

Change the view controller to `CustomCapacitorViewController` in `Main.storyboard`.

```diff
<?xml version="1.0" encoding="UTF-8"?>
<document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0" toolsVersion="14111" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none" useAutolayout="YES" useTraitCollections="YES" colorMatched="YES" initialViewController="BYZ-38-t0r">
    <device id="retina4_7" orientation="portrait">
        <adaptation id="fullscreen"/>
    </device>
    <dependencies>
        <deployment identifier="iOS"/>
        <plugIn identifier="com.apple.InterfaceBuilder.IBCocoaTouchPlugin" version="14088"/>
    </dependencies>
    <scenes>
        <!--Bridge View Controller-->
        <scene sceneID="the-QT-ifu">
            <objects>
-                <viewController id="BYZ-38-t0r" customClass="CAPBridgeViewController" customModule="Capacitor" sceneMemberID="viewController"/>
+                <viewController id="BYZ-38-t0r" customClass="CustomCapacitorViewController" customModule="App" customModuleProvider="target" sceneMemberID="viewController"/>
                <placeholder placeholderIdentifier="IBFirstResponder" id="dkx-z0-nzr" sceneMemberID="firstResponder"/>
            </objects>
        </scene>
    </scenes>
</document>
```

Look in the example app for more details.

### 4. Setup `Info.plist`

Add the following to `ios/App/App/Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app uses the microphone for voice calls</string>

<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>audio</string>
</array>
```

1. Make sure you have the following capabilities enabled in Xcode:

- Push Notifications
- Background Modes

1. Generate the certificate for Push Notifications

In order to generate the certificate for Push Notifications, you need to follow these steps:

1. Generate the signing certificate for your app.

```bash
openssl genrsa -out ALDsigning.key 2048
```

1. Generate the signing request.

```bash
openssl req -new -key ALDsigning.key -out csr3072ALDSigning.certSigningRequest -subj "/emailAddress=example@example.com, CN=Example Name, C=IE"
```

Then, please upload it to your Apple Developer account [here](https://developer.apple.com/account/resources/certificates/add). Search for `Create a new VoIP Services Certificate`

1. Download the file provided by Apple.

2. Extract the .p12 file from the downloaded file.

```bash
openssl pkcs12 -export -out voip_services.p12 -inkey ALDsigning.key -in voip_services.cer
```

1. Export the `cert.pem` and `key.pem` files from the .p12 file.

```bash
openssl pkcs12 -in voip_services.p12 -nokeys -out cert.pem -nodes
openssl pkcs12 -in voip_services.p12 -nocerts -out key.pem -nodes
```

1. Upload the `cert.pem` and `key.pem` files twilio.

```bash
npx twilio api:chat:v2:credentials:create --type=apn --sandbox --friendly-name="voice-push-credential (sandbox)" --certificate="$(cat /Users/your_username/Documents/twilio-voip/cert.pem)" --private-key="$(cat /Users/your_username/Documents/twilio-voip/key.pem)"
```

## Android Setup

### 1. Firebase Setup

Add Firebase to your Android project:

1. Add `google-services.json` to `android/app/`

2. Add `CapacitorApplication.java` to `android/app/src/main/java/YOUR_APP_PACKAGE/CapacitorApplication.java`
Copy the content of `example-app/android/app/src/main/java/com/example/plugin/CapacitorApplication.java` to your `android/app/src/main/java/YOUR_APP_PACKAGE/CapacitorApplication.java` file.

3. Import `androidx.webkit:webkit` in `build.gradle` (module :app)

```diff
dependencies {
+     implementation 'androidx.webkit:$androidxWebkitVersion'
}
```

1. Modify `MainActivity.java`

Add the following to `MainActivity.java`:

```diff
package com.example.plugin;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSExport;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginHandle;

+ import java.util.ArrayList;
+ import java.util.Collections;

import ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
+         
+         this.bridge.registerPluginInstance(CapacitorTwilioVoicePlugin.getInstance());
+         ArrayList<PluginHandle> pluginHandles = new ArrayList<>();
+         pluginHandles.add(this.bridge.getPlugin("CapacitorTwilioVoice"));
+         String pluginJS = JSExport.getPluginJS(pluginHandles);
+         if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
+             String allowedOrigin = Uri.parse(this.bridge.getAppUrl()).buildUpon().path(null).fragment(null).clearQuery().build().toString();
+             try {
+                 WebViewCompat.addDocumentStartJavaScript(this.getBridge().getWebView(), pluginJS, Collections.singleton(allowedOrigin));
+             } catch (IllegalArgumentException ex) {
+                 Logger.warn("Invalid url, using fallback");
+             }
+         }
    }
}
```

1. Register the plugin in JS

```diff
+ import { CapacitorTwilioVoice } from '@capgo/capacitor-twilio-voice';
+ import { Capacitor } from '@capacitor/core';

+ Capacitor.registerPlugin('CapacitorTwilioVoice');
```

1. Add `android:name="CapacitorApplication"` to the `application` tag in `android/app/src/main/AndroidManifest.xml`

2. Add the following to `android/app/src/main/AndroidManifest.xml`:

```xml
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.TURN_SCREEN_ON" />
    <uses-permission android:name="android.permission.SHOW_WHEN_LOCKED" />
```

Keep in mind, this will make it so that you app can be accessed when the screen is locked.

## Web/Electron Setup

The web implementation uses the [Twilio Voice JS SDK](https://www.twilio.com/docs/voice/sdks/javascript) (`@twilio/voice-sdk`) and works in both browsers and Electron.

### 1. Serve over HTTPS

Microphone access (`getUserMedia`) requires a secure context. In development you can use `localhost`; in production your app must be served over HTTPS.

### 2. No native configuration needed

Unlike iOS and Android, the web platform does not require any native project modifications, push notification certificates, or Firebase setup. The plugin registers itself automatically via `registerPlugin` in `src/index.ts`.

### 3. Audio device selection (optional)

The web implementation supports selecting specific microphones and speakers during a call:

```typescript
// List available devices (requires microphone permission)
const { inputs, outputs } = await CapacitorTwilioVoice.getAudioDevices();

// Select a specific microphone
await CapacitorTwilioVoice.setInputDevice({ deviceId: inputs[0].deviceId });

// Select a specific speaker (requires browser setSinkId support)
await CapacitorTwilioVoice.setOutputDevice({ deviceId: outputs[0].deviceId });

// Listen for device changes (e.g., headphones plugged in)
CapacitorTwilioVoice.addListener('audioDevicesChanged', (data) => {
  console.log('Devices changed:', data.inputs, data.outputs);
});
```

> **Note:** `getAudioDevices()`, `setInputDevice()`, `setOutputDevice()`, and the `audioDevicesChanged` event are web/Electron only. On iOS and Android they return empty arrays or no-op respectively.

### 4. DTMF tones

Sending DTMF digits is supported on all platforms (iOS, Android, and Web):

```typescript
// Send digits during an active call
await CapacitorTwilioVoice.sendDigits({ digits: '1234' });

// Send digits with pauses (w = 0.5s pause)
await CapacitorTwilioVoice.sendDigits({ digits: '1w2w3' });
```

Valid characters: `0-9`, `*`, `#`, and `w` (0.5 second pause).

### Platform differences

| Feature | iOS | Android | Web/Electron |
|---------|-----|---------|--------------|
| Incoming calls (push) | VoIP push (PushKit) | FCM push | Twilio JS SDK `incoming` event |
| CallKit / Telecom integration | ✅ | ✅ | N/A |
| Audio device selection | OS-managed | OS-managed | Programmatic (`getAudioDevices`, `setInputDevice`, `setOutputDevice`) |
| DTMF (`sendDigits`) | ✅ | ✅ | ✅ |
| Speaker toggle | Hardware routing | AudioSwitch | `setSinkId` API (best-effort) |

## Twilio Setup

- [iOS Setup](https://www.twilio.com/docs/voice/sdks/ios/get-started)
- [Android Setup](https://www.twilio.com/docs/voice/sdks/android/get-started)
- [Web/JavaScript Setup](https://www.twilio.com/docs/voice/sdks/javascript/get-started)

## Caller Name Display (CapacitorTwilioCallerName)

By default, incoming calls display the caller's phone number or client ID. You can customize this by passing a `CapacitorTwilioCallerName` parameter from your TwiML backend to display a friendly name instead.

### Backend Setup

When generating your TwiML response for the `<Client>` dial, add the `CapacitorTwilioCallerName` parameter:

```java
// Java example (see exemple-backend for full implementation)
Parameter callerNameParam = new Parameter.Builder()
    .name("CapacitorTwilioCallerName")
    .value("John Doe")
    .build();

Client client = new Client.Builder(identity)
    .parameter(callerNameParam)
    .build();

Dial dial = new Dial.Builder()
    .client(client)
    .build();
```

```javascript
// Node.js example
const VoiceResponse = require('twilio').twiml.VoiceResponse;

const response = new VoiceResponse();
const dial = response.dial();
dial.client({
  name: 'CapacitorTwilioCallerName',
  value: 'John Doe'
}, identity);
```

### How It Works

1. When your backend receives an incoming call, it generates TwiML to route the call
2. Include the `CapacitorTwilioCallerName` parameter with the caller's display name
3. The plugin automatically extracts this parameter and uses it for:
   - iOS CallKit incoming call screen
   - Android incoming call notification
   - The `from` field in `callInviteReceived` events
   - The `pendingInvites` array in `getCallStatus()`

If `CapacitorTwilioCallerName` is not provided, the plugin falls back to the caller's phone number or client ID.

## Usage

### Authentication

#### `login(options: { accessToken: string })`

Authenticates with Twilio using a JWT access token:

- Validates token expiration automatically
- Stores token securely for app restarts  
- Registers for VoIP push notifications
- **Note**: The plugin will reject expired tokens

#### `logout()`

Logs out the current user and cleans up all session data:

- Unregisters from VoIP push notifications
- Clears stored access tokens
- Ends any active calls
- Resets all call state

#### `isLoggedIn()`

Checks if user is currently logged in with a valid (non-expired) token.
Returns: `{ isLoggedIn: boolean, hasValidToken: boolean, identity?: string }`

The `identity` field contains the user identity extracted from the JWT token if logged in.

#### `makeCall(options: { to: string })`

Initiates an outgoing call. Requires prior authentication via `login()`.

#### `acceptCall(options: { callSid: string })`

Accepts an incoming call.

#### `rejectCall(options: { callSid: string })`

Rejects an incoming call.

#### `endCall(options?: { callSid?: string })`

Ends the active call or a specific call.

#### `muteCall(options: { muted: boolean, callSid?: string })`

Mutes or unmutes the microphone.

#### `sendDigits(options: { digits: string, callSid?: string })`

Sends DTMF digits during an active call. Valid characters are 0-9, *, #, and w (for 0.5s pause).

#### `setSpeaker(options: { enabled: boolean })`

Enables or disables the speaker. On Android, uses Twilio AudioSwitch to manage audio routing between earpiece, speaker, and connected devices (headsets, Bluetooth, etc.).

#### `getCallStatus()`

Gets the current call status.

#### `checkMicrophonePermission()`

Checks if microphone permission is granted.

#### `requestMicrophonePermission()`

Requests microphone permission from the user.

### Event Listeners

```typescript
import { CapacitorTwilioVoice } from '@capgo/capacitor-twilio-voice';

// Registration events
CapacitorTwilioVoice.addListener('registrationSuccess', (data) => {
  console.log('Successfully registered:', data);
});

CapacitorTwilioVoice.addListener('registrationFailure', (data) => {
  console.error('Registration failed:', data);
});

// Call events
CapacitorTwilioVoice.addListener('callInviteReceived', (data) => {
  console.log('Incoming call from:', data.from);
});

CapacitorTwilioVoice.addListener('callConnected', (data) => {
  console.log('Call connected:', data);
});

CapacitorTwilioVoice.addListener('callDisconnected', (data) => {
  console.log('Call ended:', data);
});

CapacitorTwilioVoice.addListener('callRinging', (data) => {
  console.log('Call is ringing:', data);
});

CapacitorTwilioVoice.addListener('callReconnecting', (data) => {
  console.log('Call reconnecting:', data);
});

CapacitorTwilioVoice.addListener('callReconnected', (data) => {
  console.log('Call reconnected:', data);
});

CapacitorTwilioVoice.addListener('callQualityWarningsChanged', (data) => {
  console.log('Quality warnings:', data);
});
```

## JWT Token Management

### Token Format

The plugin expects Twilio access tokens in JWT format with this structure:

```json
{
  "iss": "your-account-sid",
  "exp": 1234567890,
  "grants": {
    "voice": {
      "outgoing": {
        "application_sid": "your-app-sid"
      },
      "push_credential_sid": "your-push-credential-sid"
    },
    "identity": "user-identity"
  }
}
```

### Token Validation

- Tokens are automatically validated for expiration
- Invalid or expired tokens will be rejected
- Use `isLoggedIn()` to check token status

### Backend Integration

Fetch access tokens from your backend server:

```typescript
async function fetchAccessToken(identity: string): Promise<string> {
  const response = await fetch(`/accessToken?identity=${identity}`);
  return response.text();
}
```

## Testing Requirements

### iOS Simulator Limitations

- VoIP push notifications don't work in the iOS Simulator
- Use a physical iOS device for testing incoming calls
- Outgoing calls work in both Simulator and device

### Android Emulator

- Requires Google Play Services
- Firebase messaging works in Android Emulator with Google APIs

### Web/Electron

- Works in modern browsers (Chrome, Edge, Firefox, Safari 14.1+)
- Microphone access requires HTTPS or `localhost`
- Audio device selection (`setInputDevice`/`setOutputDevice`) requires browser support for `setSinkId`
- No push notifications — incoming calls are delivered via the Twilio JS SDK's `incoming` event while the page is open
- DTMF tones (`sendDigits`) work on all platforms including web

## Error Handling

The plugin provides detailed error information:

```typescript
try {
  await CapacitorTwilioVoice.makeCall({ to: '+1234567890' });
} catch (error) {
  console.error('Call failed:', error);
}
```

Common error scenarios:

- **Invalid token**: Check token format and expiration
- **No microphone permission**: Call `requestMicrophonePermission()`
- **Network issues**: Verify internet connectivity
- **Invalid phone number**: Use E.164 format (+1234567890)

## Security Notes

- Access tokens are stored in secure device storage
- Tokens are automatically validated before use
- No sensitive data is logged in production builds
- Always use HTTPS for token fetching from your backend

## Platform Support

| Platform | Support | Notes |
|----------|---------|-------|
| iOS      | ✅      | Requires iOS 13.0+ |
| Android  | ✅      | Requires API level 23+ |
| Web      | ✅      | Supports browser and Electron. Audio device selection (microphone/speaker) supported. |

## API

<docgen-index>

* [`login(...)`](#login)
* [`logout()`](#logout)
* [`isLoggedIn()`](#isloggedin)
* [`makeCall(...)`](#makecall)
* [`acceptCall(...)`](#acceptcall)
* [`rejectCall(...)`](#rejectcall)
* [`endCall(...)`](#endcall)
* [`muteCall(...)`](#mutecall)
* [`sendDigits(...)`](#senddigits)
* [`setSpeaker(...)`](#setspeaker)
* [`getCallStatus()`](#getcallstatus)
* [`checkMicrophonePermission()`](#checkmicrophonepermission)
* [`requestMicrophonePermission()`](#requestmicrophonepermission)
* [`getAudioDevices()`](#getaudiodevices)
* [`setInputDevice(...)`](#setinputdevice)
* [`setOutputDevice(...)`](#setoutputdevice)
* [`addListener('callInviteReceived', ...)`](#addlistenercallinvitereceived-)
* [`addListener('callConnected', ...)`](#addlistenercallconnected-)
* [`addListener('callInviteCancelled', ...)`](#addlistenercallinvitecancelled-)
* [`addListener('outgoingCallInitiated', ...)`](#addlisteneroutgoingcallinitiated-)
* [`addListener('outgoingCallFailed', ...)`](#addlisteneroutgoingcallfailed-)
* [`addListener('callDisconnected', ...)`](#addlistenercalldisconnected-)
* [`addListener('callRinging', ...)`](#addlistenercallringing-)
* [`addListener('callReconnecting', ...)`](#addlistenercallreconnecting-)
* [`addListener('callReconnected', ...)`](#addlistenercallreconnected-)
* [`addListener('callQualityWarningsChanged', ...)`](#addlistenercallqualitywarningschanged-)
* [`addListener('registrationSuccess', ...)`](#addlistenerregistrationsuccess-)
* [`addListener('registrationFailure', ...)`](#addlistenerregistrationfailure-)
* [`addListener('audioDevicesChanged', ...)`](#addlisteneraudiodeviceschanged-)
* [`removeAllListeners()`](#removealllisteners)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### login(...)

```typescript
login(options: { accessToken: string; }) => Promise<{ success: boolean; }>
```

Authenticate the user with Twilio Voice using an access token.

The access token should be generated on your backend server using your Twilio credentials.
This token is required to make and receive calls through Twilio Voice.

| Param         | Type                                  | Description            |
| ------------- | ------------------------------------- | ---------------------- |
| **`options`** | <code>{ accessToken: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### logout()

```typescript
logout() => Promise<{ success: boolean; }>
```

Log out the current user and unregister from Twilio Voice.

This will disconnect any active calls and stop the device from receiving
new incoming call notifications.

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### isLoggedIn()

```typescript
isLoggedIn() => Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string; }>
```

Check if the user is currently logged in and has a valid access token.

**Returns:** <code>Promise&lt;{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string; }&gt;</code>

--------------------


### makeCall(...)

```typescript
makeCall(options: { to: string; params?: Record<string, string>; }) => Promise<{ success: boolean; callSid?: string; }>
```

Initiate an outgoing call to a phone number or client.

The user must be logged in before making a call. The call will be routed
through your Twilio backend configuration.

| Param         | Type                                                                                      | Description            |
| ------------- | ----------------------------------------------------------------------------------------- | ---------------------- |
| **`options`** | <code>{ to: string; params?: <a href="#record">Record</a>&lt;string, string&gt;; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; callSid?: string; }&gt;</code>

--------------------


### acceptCall(...)

```typescript
acceptCall(options: { callSid: string; }) => Promise<{ success: boolean; }>
```

Accept an incoming call.

This should be called in response to a 'callInviteReceived' event.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ callSid: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### rejectCall(...)

```typescript
rejectCall(options: { callSid: string; }) => Promise<{ success: boolean; }>
```

Reject an incoming call.

This should be called in response to a 'callInviteReceived' event.
The caller will hear a busy signal or be directed to voicemail.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ callSid: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### endCall(...)

```typescript
endCall(options: { callSid?: string; }) => Promise<{ success: boolean; }>
```

End an active call.

If callSid is not provided, this will end the currently active call.

| Param         | Type                               | Description            |
| ------------- | ---------------------------------- | ---------------------- |
| **`options`** | <code>{ callSid?: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### muteCall(...)

```typescript
muteCall(options: { muted: boolean; callSid?: string; }) => Promise<{ success: boolean; }>
```

Mute or unmute the microphone during an active call.

When muted, the other party will not hear audio from your microphone.

| Param         | Type                                               | Description            |
| ------------- | -------------------------------------------------- | ---------------------- |
| **`options`** | <code>{ muted: boolean; callSid?: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### sendDigits(...)

```typescript
sendDigits(options: { digits: string; callSid?: string; }) => Promise<{ success: boolean; }>
```

Send DTMF digits during an active call.

| Param         | Type                                               | Description            |
| ------------- | -------------------------------------------------- | ---------------------- |
| **`options`** | <code>{ digits: string; callSid?: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### setSpeaker(...)

```typescript
setSpeaker(options: { enabled: boolean; }) => Promise<{ success: boolean; }>
```

Enable or disable speakerphone mode.

When enabled, audio will be routed through the device's speaker instead of the earpiece.

| Param         | Type                               | Description            |
| ------------- | ---------------------------------- | ---------------------- |
| **`options`** | <code>{ enabled: boolean; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### getCallStatus()

```typescript
getCallStatus() => Promise<{ hasActiveCall: boolean; isOnHold: boolean; isMuted: boolean; callSid?: string; callState?: string; pendingInvites: CallInvite[]; activeCallsCount: number; }>
```

Get the current status of the active call.

This provides real-time information about the call state, mute status,
hold status, and call identifiers.

**Returns:** <code>Promise&lt;{ hasActiveCall: boolean; isOnHold: boolean; isMuted: boolean; callSid?: string; callState?: string; pendingInvites: CallInvite[]; activeCallsCount: number; }&gt;</code>

--------------------


### checkMicrophonePermission()

```typescript
checkMicrophonePermission() => Promise<{ granted: boolean; }>
```

Check if microphone permission has been granted.

This does not request permission, only checks the current permission status.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### requestMicrophonePermission()

```typescript
requestMicrophonePermission() => Promise<{ granted: boolean; }>
```

Request microphone permission from the user.

On iOS and Android, this will show the system permission dialog if permission
has not been granted yet. If permission was previously denied, the user may need
to grant it in system settings.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### getAudioDevices()

```typescript
getAudioDevices() => Promise<{ inputs: AudioDevice[]; outputs: AudioDevice[]; }>
```

Get available audio input and output devices.

On web/Electron: Enumerates system audio devices using the Web Audio API.
Requires microphone permission to have been granted for device labels to be available.
On iOS/Android: Returns empty arrays (audio routing is handled by the OS).

**Returns:** <code>Promise&lt;{ inputs: AudioDevice[]; outputs: AudioDevice[]; }&gt;</code>

--------------------


### setInputDevice(...)

```typescript
setInputDevice(options: { deviceId: string; }) => Promise<{ success: boolean; }>
```

Select a specific audio input device (microphone).

On web/Electron: Routes microphone input through the specified device.
The device stays active until another input is selected, the call ends,
or `logout()` is called.
On iOS/Android: No-op, returns `{ success: true }`.

| Param         | Type                               | Description            |
| ------------- | ---------------------------------- | ---------------------- |
| **`options`** | <code>{ deviceId: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### setOutputDevice(...)

```typescript
setOutputDevice(options: { deviceId: string; }) => Promise<{ success: boolean; }>
```

Select a specific audio output device (speaker/headphones).

On web/Electron: Routes call audio and ringtone through the specified device.
Requires browser support for the `setSinkId` API. Check `getAudioDevices()` for
available outputs — if the array is empty, output selection is not supported.
On iOS/Android: No-op, returns `{ success: true }`.

| Param         | Type                               | Description            |
| ------------- | ---------------------------------- | ---------------------- |
| **`options`** | <code>{ deviceId: string; }</code> | - Configuration object |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### addListener('callInviteReceived', ...)

```typescript
addListener(eventName: 'callInviteReceived', listenerFunc: (data: CallInvite) => void) => Promise<PluginListenerHandle>
```

Listen for incoming call invitations.

This event is fired when another user or phone number is calling you.
You should call acceptCall() or rejectCall() in response.

| Param              | Type                                                                 | Description                             |
| ------------------ | -------------------------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callInviteReceived'</code>                                    | - The event name ('callInviteReceived') |
| **`listenerFunc`** | <code>(data: <a href="#callinvite">CallInvite</a>) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callConnected', ...)

```typescript
addListener(eventName: 'callConnected', listenerFunc: (data: { callSid: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for call connected events.

This event is fired when a call (incoming or outgoing) has been successfully
connected and audio can be heard.

| Param              | Type                                                 | Description                             |
| ------------------ | ---------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callConnected'</code>                         | - The event name ('callConnected')      |
| **`listenerFunc`** | <code>(data: { callSid: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callInviteCancelled', ...)

```typescript
addListener(eventName: 'callInviteCancelled', listenerFunc: (data: { callSid: string; reason: 'user_declined' | 'remote_cancelled'; }) => void) => Promise<PluginListenerHandle>
```

Listen for call invite cancellation events.

This event is fired when an incoming call invitation is cancelled before being
answered, either by the caller hanging up or by the user declining.

| Param              | Type                                                                                                | Description                              |
| ------------------ | --------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| **`eventName`**    | <code>'callInviteCancelled'</code>                                                                  | - The event name ('callInviteCancelled') |
| **`listenerFunc`** | <code>(data: { callSid: string; reason: 'user_declined' \| 'remote_cancelled'; }) =&gt; void</code> | - Callback function to handle the event  |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('outgoingCallInitiated', ...)

```typescript
addListener(eventName: 'outgoingCallInitiated', listenerFunc: (data: { callSid: string; to: string; source: 'app' | 'system'; displayName?: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for outgoing call initiation events.

This event is fired when an outgoing call is initiated, either from the app
or from the system (e.g., CallKit on iOS, Telecom on Android).

| Param              | Type                                                                                                              | Description                                |
| ------------------ | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **`eventName`**    | <code>'outgoingCallInitiated'</code>                                                                              | - The event name ('outgoingCallInitiated') |
| **`listenerFunc`** | <code>(data: { callSid: string; to: string; source: 'app' \| 'system'; displayName?: string; }) =&gt; void</code> | - Callback function to handle the event    |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('outgoingCallFailed', ...)

```typescript
addListener(eventName: 'outgoingCallFailed', listenerFunc: (data: { callSid: string; to: string; reason: 'missing_access_token' | 'connection_failed' | 'no_call_details' | 'microphone_permission_denied' | 'invalid_contact' | 'callkit_request_failed' | 'unsupported_intent'; displayName?: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for outgoing call failure events.

This event is fired when an outgoing call fails to connect due to various reasons
such as missing credentials, permission issues, or network problems.

| Param              | Type                                                                                                                                                                                                                                                                          | Description                             |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'outgoingCallFailed'</code>                                                                                                                                                                                                                                             | - The event name ('outgoingCallFailed') |
| **`listenerFunc`** | <code>(data: { callSid: string; to: string; reason: 'missing_access_token' \| 'connection_failed' \| 'no_call_details' \| 'microphone_permission_denied' \| 'invalid_contact' \| 'callkit_request_failed' \| 'unsupported_intent'; displayName?: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callDisconnected', ...)

```typescript
addListener(eventName: 'callDisconnected', listenerFunc: (data: { callSid: string; error?: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for call disconnection events.

This event is fired when a call ends, either normally or due to an error.

| Param              | Type                                                                 | Description                             |
| ------------------ | -------------------------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callDisconnected'</code>                                      | - The event name ('callDisconnected')   |
| **`listenerFunc`** | <code>(data: { callSid: string; error?: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callRinging', ...)

```typescript
addListener(eventName: 'callRinging', listenerFunc: (data: { callSid: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for call ringing events.

This event is fired when an outgoing call starts ringing on the other end.

| Param              | Type                                                 | Description                             |
| ------------------ | ---------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callRinging'</code>                           | - The event name ('callRinging')        |
| **`listenerFunc`** | <code>(data: { callSid: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callReconnecting', ...)

```typescript
addListener(eventName: 'callReconnecting', listenerFunc: (data: { callSid: string; error?: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for call reconnecting events.

This event is fired when a call loses connection and Twilio is attempting to
reconnect. The call is not disconnected yet but audio may be interrupted.

| Param              | Type                                                                 | Description                             |
| ------------------ | -------------------------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callReconnecting'</code>                                      | - The event name ('callReconnecting')   |
| **`listenerFunc`** | <code>(data: { callSid: string; error?: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callReconnected', ...)

```typescript
addListener(eventName: 'callReconnected', listenerFunc: (data: { callSid: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for call reconnected events.

This event is fired when a call successfully reconnects after a connection loss.
Audio should resume normally after this event.

| Param              | Type                                                 | Description                             |
| ------------------ | ---------------------------------------------------- | --------------------------------------- |
| **`eventName`**    | <code>'callReconnected'</code>                       | - The event name ('callReconnected')    |
| **`listenerFunc`** | <code>(data: { callSid: string; }) =&gt; void</code> | - Callback function to handle the event |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('callQualityWarningsChanged', ...)

```typescript
addListener(eventName: 'callQualityWarningsChanged', listenerFunc: (data: { callSid: string; currentWarnings: string[]; previousWarnings: string[]; }) => void) => Promise<PluginListenerHandle>
```

Listen for call quality warning events.

This event is fired when the call quality changes, providing warnings about
potential issues like high jitter, packet loss, or low audio levels.

| Param              | Type                                                                                                        | Description                                     |
| ------------------ | ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| **`eventName`**    | <code>'callQualityWarningsChanged'</code>                                                                   | - The event name ('callQualityWarningsChanged') |
| **`listenerFunc`** | <code>(data: { callSid: string; currentWarnings: string[]; previousWarnings: string[]; }) =&gt; void</code> | - Callback function to handle the event         |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('registrationSuccess', ...)

```typescript
addListener(eventName: 'registrationSuccess', listenerFunc: () => void) => Promise<PluginListenerHandle>
```

Listen for successful registration events.

This event is fired when the device successfully registers with Twilio Voice
and is ready to make and receive calls. This typically occurs after a successful
login with a valid access token.

| Param              | Type                               | Description                              |
| ------------------ | ---------------------------------- | ---------------------------------------- |
| **`eventName`**    | <code>'registrationSuccess'</code> | - The event name ('registrationSuccess') |
| **`listenerFunc`** | <code>() =&gt; void</code>         | - Callback function to handle the event  |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('registrationFailure', ...)

```typescript
addListener(eventName: 'registrationFailure', listenerFunc: (data: { error: string; }) => void) => Promise<PluginListenerHandle>
```

Listen for registration failure events.

This event is fired when the device fails to register with Twilio Voice,
typically due to an invalid or expired access token, network issues, or
Twilio service problems.

| Param              | Type                                               | Description                              |
| ------------------ | -------------------------------------------------- | ---------------------------------------- |
| **`eventName`**    | <code>'registrationFailure'</code>                 | - The event name ('registrationFailure') |
| **`listenerFunc`** | <code>(data: { error: string; }) =&gt; void</code> | - Callback function to handle the event  |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('audioDevicesChanged', ...)

```typescript
addListener(eventName: 'audioDevicesChanged', listenerFunc: (data: { inputs: AudioDevice[]; outputs: AudioDevice[]; }) => void) => Promise<PluginListenerHandle>
```

Listen for audio device changes.

This event is fired when audio input/output devices are added or removed
from the system (e.g., plugging in headphones, connecting Bluetooth).
Web/Electron only — this event is never fired on iOS/Android.

| Param              | Type                                                                               | Description                              |
| ------------------ | ---------------------------------------------------------------------------------- | ---------------------------------------- |
| **`eventName`**    | <code>'audioDevicesChanged'</code>                                                 | - The event name ('audioDevicesChanged') |
| **`listenerFunc`** | <code>(data: { inputs: AudioDevice[]; outputs: AudioDevice[]; }) =&gt; void</code> | - Callback function to handle the event  |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all registered event listeners.

This is useful for cleanup when your component unmounts or when you want to
reset all event handling.

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native Capacitor plugin version

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

--------------------


### Interfaces


#### CallInvite

Represents a pending incoming call invitation.

This interface describes the data structure for call invitations that have been received
but not yet accepted or rejected. The same structure is used both in the
`callInviteReceived` event and in the `pendingInvites` array returned by `getCallStatus()`.

| Prop               | Type                                                            | Description                                                                      |
| ------------------ | --------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| **`callSid`**      | <code>string</code>                                             | Unique identifier for the incoming call invitation                               |
| **`from`**         | <code>string</code>                                             | Phone number or client identifier of the caller (may include custom caller name) |
| **`to`**           | <code>string</code>                                             | Phone number or client identifier being called                                   |
| **`customParams`** | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Custom parameters passed with the call invitation                                |


#### AudioDevice

Represents an audio device (microphone or speaker) available for use.

This interface is used by the web implementation to enumerate and select
specific audio input/output devices. On iOS and Android, audio routing is
handled by the OS (earpiece/speaker toggle, Bluetooth).

| Prop           | Type                                       | Description                                                       |
| -------------- | ------------------------------------------ | ----------------------------------------------------------------- |
| **`deviceId`** | <code>string</code>                        | Browser-assigned unique identifier for this device                |
| **`label`**    | <code>string</code>                        | Human-readable label (e.g., "Built-in Microphone", "AirPods Pro") |
| **`kind`**     | <code>'audioinput' \| 'audiooutput'</code> | Whether this is an input (microphone) or output (speaker) device  |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{
 [P in K]: T;
 }</code>

</docgen-api>
