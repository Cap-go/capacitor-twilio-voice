## Capacitor Twilio Voice Plugin

A Capacitor plugin for integrating Twilio Voice calling functionality into iOS and Android applications.

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


### 3. Edit `Main.storyboard`

Change the view controller to `CustomCapacitorViewController` in `Main.storyboard`.

```xml
<viewController id="BYZ-38-t0r" customClass="CustomCapacitorViewController" customModule="App" customModuleProvider="target" sceneMemberID="viewController"/>
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

5. Make sure you have the following capabilities enabled in Xcode:

- Push Notifications
- Background Modes

## Android Setup

### 1. Firebase Setup
Add Firebase to your Android project:

1. Add `google-services.json` to `android/app/`

2. Install `patch-package`

```bash
npm install patch-package
```
3. Hack capacitor by copying the patch from `example-app/patches/@capacitor+android+7.0.0.patch` to the `patches` folder of your app.

4. Run `patch-package`

```bash
npx patch-package
```

5. Add `CapacitorApplication.java` to `android/app/src/main/java/YOUR_APP_PACKAGE/CapacitorApplication.java`
Copy the content of `example-app/android/app/src/main/java/com/example/plugin/CapacitorApplication.java` to your `android/app/src/main/java/YOUR_APP_PACKAGE/CapacitorApplication.java` file.

6. Add `android:name="CapacitorApplication"` to the `application` tag in `android/app/src/main/AndroidManifest.xml`

7. Add the following to `android/app/src/main/AndroidManifest.xml`:

```xml
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.TURN_SCREEN_ON" />
    <uses-permission android:name="android.permission.SHOW_WHEN_LOCKED" />
```

Keep in mind, this will make it so that you app can be accessed when the screen is locked.

## Twilio Setup

 - [iOS Setup](https://www.twilio.com/docs/voice/sdks/ios/get-started)
 - [Android Setup](https://www.twilio.com/docs/voice/sdks/android/get-started)

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
| Web      | ❌      | Not supported |
