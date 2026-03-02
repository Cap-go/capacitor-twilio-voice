/**
 * Capacitor plugin for integrating Twilio Voice functionality into mobile applications.
 *
 * This plugin provides comprehensive voice call capabilities including:
 * - User authentication with Twilio access tokens
 * - Making and receiving phone calls
 * - Call management (accept, reject, end, mute)
 * - Audio routing controls (speaker mode)
 * - Real-time call status monitoring
 * - Event-driven architecture for call lifecycle events
 * - Microphone permission handling
 * - Audio device selection (web/Electron only)
 *
 * @example
 * ```typescript
 * import { CapacitorTwilioVoice } from 'capacitor-twilio-voice';
 *
 * // Login with access token
 * await CapacitorTwilioVoice.login({ accessToken: 'your-twilio-token' });
 *
 * // Make a call
 * await CapacitorTwilioVoice.makeCall({ to: '+1234567890' });
 *
 * // Listen for incoming calls
 * CapacitorTwilioVoice.addListener('callInviteReceived', (data) => {
 *   console.log('Incoming call from:', data.from);
 * });
 * ```
 */
export {};
//# sourceMappingURL=definitions.js.map