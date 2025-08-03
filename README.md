# @capgo/capacitor-twilio-voice

Capacitor plugin for Twilio Voice SDK

## Installation

```bash
npm install @capgo/capacitor-twilio-voice
npx cap sync
```

## Authentication

This plugin uses a login-based authentication system where you provide Twilio access tokens at runtime. The plugin automatically validates JWT tokens and handles token expiration.

### Backend Integration

The example app demonstrates how to integrate with a Twilio backend server to fetch access tokens dynamically. The included Java backend at [https://twilio-backend-82.localcan.dev](https://twilio-backend-82.localcan.dev) provides:

- `/accessToken?identity=alice` - Returns a fresh JWT access token for the specified identity
- CORS support for web applications
- TwiML endpoints for call handling

## Usage

### Option 1: Direct Token Usage
```typescript
import { CapacitorTwilioVoice } from '@capgo/capacitor-twilio-voice';

// Login with your Twilio access token
await CapacitorTwilioVoice.login({ 
  accessToken: 'YOUR_TWILIO_JWT_ACCESS_TOKEN' 
});
```

### Option 2: Backend Integration (Recommended)
```typescript
// Fetch token from your backend
async function fetchAccessToken(identity) {
  const response = await fetch(`${BACKEND_URL}/accessToken?identity=${identity}`);
  return await response.text();
}

// Login with fetched token
const accessToken = await fetchAccessToken('alice');
await CapacitorTwilioVoice.login({ accessToken });

// Make a call
const { callSid } = await CapacitorTwilioVoice.makeCall({ to: '+1234567890' });

// Logout when done
await CapacitorTwilioVoice.logout();

// Listen for incoming calls
CapacitorTwilioVoice.addListener('callInviteReceived', (data) => {
  console.log('Incoming call from:', data.from);
  // Accept or reject the call
  await CapacitorTwilioVoice.acceptCall({ callSid: data.callSid });
});

// Listen for call state changes
CapacitorTwilioVoice.addListener('callConnected', (data) => {
  console.log('Call connected:', data.callSid);
});

CapacitorTwilioVoice.addListener('callDisconnected', (data) => {
  console.log('Call ended:', data.callSid);
});
```

## API

### Methods

#### `login(options: { accessToken: string })`
Authenticates with Twilio using a JWT access token. The plugin automatically:
- Validates the JWT format and expiration
- Stores the token securely for reuse
- Registers for VoIP push notifications
- **Note**: The plugin will reject expired tokens

#### `logout()`
Logs out the current user and cleans up all session data:
- Unregisters from VoIP push notifications
- Clears stored access tokens
- Ends any active calls
- Resets all call state

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
Enables or disables the speaker.

#### `getCallStatus()`
Gets the current call status.

#### `checkMicrophonePermission()`
Checks if microphone permission is granted.

#### `requestMicrophonePermission()`
Requests microphone permission.

### Events

- `callInviteReceived` - Incoming call received
- `callConnected` - Call connected
- `callDisconnected` - Call ended
- `callRinging` - Outgoing call is ringing
- `callReconnecting` - Call is reconnecting
- `callReconnected` - Call reconnected
- `callQualityWarningsChanged` - Call quality warnings
- `registrationSuccess` - Successfully registered for VoIP
- `registrationFailure` - Failed to register for VoIP

## JWT Token Management

The plugin automatically handles JWT token validation and expiration:

- **Validation**: Tokens are validated on login and before each operation
- **Expiration**: Expired tokens are automatically rejected
- **Storage**: Valid tokens are stored persistently using UserDefaults
- **Reuse**: Stored tokens are automatically loaded on app restart (if still valid)

### Token Requirements

Your Twilio JWT access token must:
- Be properly formatted (3 parts separated by dots)
- Contain a valid `exp` (expiration) claim
- Not be expired at the time of use

## iOS Setup

### 1. Add Required Permissions
Add the following to your `Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app uses the microphone to make and receive voice calls through Twilio Voice.</string>

<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
</array>
```

### 2. Push Notifications Entitlement
Ensure your app has the Push Notifications capability enabled in Xcode.

### 3. VoIP Push Certificate
Set up VoIP push notifications in your Apple Developer account and configure your Twilio application accordingly.

## Error Handling

The plugin provides comprehensive error handling:

```typescript
try {
  await CapacitorTwilioVoice.login({ accessToken: 'your-token' });
} catch (error) {
  if (error.message.includes('expired')) {
    // Token is expired, get a new one
    console.log('Token expired, please refresh');
  } else if (error.message.includes('Invalid')) {
    // Token format is invalid
    console.log('Invalid token format');
  }
}

try {
  await CapacitorTwilioVoice.makeCall({ to: '+1234567890' });
} catch (error) {
  if (error.message.includes('No access token')) {
    // Need to login first
    console.log('Please login first');
  } else if (error.message.includes('expired')) {
    // Token expired during operation
    console.log('Token expired, please login again');
  }
}
```

## Security Notes

- Access tokens are stored in UserDefaults (not encrypted)
- Tokens are validated client-side for expiration
- Always use HTTPS when transmitting tokens
- Implement token refresh logic in your application
- Consider the security implications of your token storage strategy

## Android Support

⚠️ **Note**: This plugin currently supports iOS only. Android support is planned for future releases.

## License

MIT
