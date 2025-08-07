## Capacitor Twilio Voice Plugin
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

A Capacitor plugin for integrating Twilio Voice calling functionality into iOS and Android applications.

## Installation

```bash
npm install @capgo/capacitor-twilio-voice
npx cap sync
```

## iOS Setup

### 1. Install Dependencies

#### Option A: CocoaPods (Recommended)
Add to your app's `ios/App/Podfile`:
```ruby
pod 'TwilioVoice', '~> 6.13'
```

#### Option B: Swift Package Manager  
Add to your `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/twilio/twilio-voice-ios", from: "6.13.0")
]
```

### 2. Configure Info.plist
Add to `ios/App/App/Info.plist`:
```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app uses the microphone for voice calls</string>

<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>audio</string>
</array>
```

### 3. Enable Push Notifications Capability
In Xcode, select your app target → Signing & Capabilities → Add Capability → Push Notifications

### 4. PushKit Setup
PushKit is automatically configured by the plugin. No additional setup required.

## Android Setup

### 1. Firebase Setup
Add Firebase to your Android project:

1. Add `google-services.json` to `android/app/`
2. Update `android/app/build.gradle`:
```gradle
apply plugin: 'com.google.gms.google-services'

dependencies {
    // ... existing dependencies
    implementation 'com.google.firebase:firebase-messaging:25.0.0'
    implementation platform('com.google.firebase:firebase-bom:34.0.0')
    implementation 'com.twilio:audioswitch:1.2.2'
    implementation 'androidx.core:core:1.13.1'
    implementation 'androidx.media:media:1.6.0'
}
```

3. Update project-level `android/build.gradle`:
```gradle
buildscript {
    dependencies {
        classpath 'com.google.gms:google-services:4.4.2'
    }
}
```

### 2. AndroidManifest.xml
Required permissions are automatically added by the plugin:
- `RECORD_AUDIO`
- `INTERNET` 
- `ACCESS_NETWORK_STATE`
- `WAKE_LOCK`
- `USE_FULL_SCREEN_INTENT`
- `VIBRATE`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`

### 3. Firebase Messaging Service
The plugin automatically registers a Firebase Messaging Service to handle incoming calls.

### 4. Android Notification Features 📱
The Android implementation includes comprehensive notification support:

- **🔔 System Notifications**: Incoming calls appear as proper Android notifications with caller information
- **📱 Full-Screen Intent**: Calls can launch the app on lock screen for immediate visibility
- **🎵 Ringtone & Vibration**: Uses system default ringtone with vibration pattern for audio/haptic alerts
- **⚡ Action Buttons**: Accept/Reject buttons directly in notification for quick response
- **🔕 Auto-Dismiss**: Notifications automatically clear when calls end or are answered
- **🌙 Do Not Disturb**: Respects system notification settings and priority modes

**Note**: Notification functionality requires notification permissions. The app will request these permissions during login if not already granted.

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
