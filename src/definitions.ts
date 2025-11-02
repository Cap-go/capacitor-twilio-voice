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

/**
 * Represents a pending incoming call invitation.
 *
 * This interface describes the data structure for call invitations that have been received
 * but not yet accepted or rejected. The same structure is used both in the
 * `callInviteReceived` event and in the `pendingInvites` array returned by `getCallStatus()`.
 *
 * @example
 * ```typescript
 * CapacitorTwilioVoice.addListener('callInviteReceived', (data: CallInvite) => {
 *   console.log('Incoming call from:', data.from);
 *   console.log('Call SID:', data.callSid);
 *   console.log('To:', data.to);
 *   console.log('Custom params:', data.customParams);
 * });
 *
 * const status = await CapacitorTwilioVoice.getCallStatus();
 * status.pendingInvites.forEach((invite: CallInvite) => {
 *   console.log('Pending call from:', invite.from);
 * });
 * ```
 */
export interface CallInvite {
  /** Unique identifier for the incoming call invitation */
  callSid: string;
  /** Phone number or client identifier of the caller (may include custom caller name) */
  from: string;
  /** Phone number or client identifier being called */
  to: string;
  /** Custom parameters passed with the call invitation */
  customParams: Record<string, string>;
}

export interface CapacitorTwilioVoicePlugin {
  // Authentication

  /**
   * Authenticate the user with Twilio Voice using an access token.
   *
   * The access token should be generated on your backend server using your Twilio credentials.
   * This token is required to make and receive calls through Twilio Voice.
   *
   * @param options - Configuration object
   * @param options.accessToken - Twilio access token obtained from your backend server
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * const result = await CapacitorTwilioVoice.login({
   *   accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
   * });
   * console.log('Login successful:', result.success);
   * ```
   */
  login(options: { accessToken: string }): Promise<{ success: boolean }>;

  /**
   * Log out the current user and unregister from Twilio Voice.
   *
   * This will disconnect any active calls and stop the device from receiving
   * new incoming call notifications.
   *
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * const result = await CapacitorTwilioVoice.logout();
   * console.log('Logout successful:', result.success);
   * ```
   */
  logout(): Promise<{ success: boolean }>;

  /**
   * Check if the user is currently logged in and has a valid access token.
   *
   * @returns Promise that resolves with login status information
   * @returns isLoggedIn - Whether the user is currently logged in
   * @returns hasValidToken - Whether the access token is still valid
   * @returns identity - The user's Twilio identity (if logged in)
   *
   * @example
   * ```typescript
   * const status = await CapacitorTwilioVoice.isLoggedIn();
   * if (status.isLoggedIn && status.hasValidToken) {
   *   console.log('User identity:', status.identity);
   * } else {
   *   // Re-authenticate the user
   * }
   * ```
   */
  isLoggedIn(): Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string }>;

  // Call Management

  /**
   * Initiate an outgoing call to a phone number or client.
   *
   * The user must be logged in before making a call. The call will be routed
   * through your Twilio backend configuration.
   *
   * @param options - Configuration object
   * @param options.to - Phone number (E.164 format) or Twilio client identifier to call
   * @returns Promise that resolves with success status and call SID
   * @returns success - Whether the call was initiated successfully
   * @returns callSid - Unique identifier for this call (if successful)
   *
   * @example
   * ```typescript
   * // Call a phone number
   * const result = await CapacitorTwilioVoice.makeCall({
   *   to: '+1234567890'
   * });
   * console.log('Call SID:', result.callSid);
   *
   * // Call another Twilio client
   * await CapacitorTwilioVoice.makeCall({
   *   to: 'client:alice'
   * });
   * ```
   */
  makeCall(options: { to: string }): Promise<{ success: boolean; callSid?: string }>;

  /**
   * Accept an incoming call.
   *
   * This should be called in response to a 'callInviteReceived' event.
   *
   * @param options - Configuration object
   * @param options.callSid - Unique identifier of the call to accept
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * CapacitorTwilioVoice.addListener('callInviteReceived', async (data) => {
   *   console.log('Incoming call from:', data.from);
   *   const result = await CapacitorTwilioVoice.acceptCall({
   *     callSid: data.callSid
   *   });
   *   console.log('Call accepted:', result.success);
   * });
   * ```
   */
  acceptCall(options: { callSid: string }): Promise<{ success: boolean }>;

  /**
   * Reject an incoming call.
   *
   * This should be called in response to a 'callInviteReceived' event.
   * The caller will hear a busy signal or be directed to voicemail.
   *
   * @param options - Configuration object
   * @param options.callSid - Unique identifier of the call to reject
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * CapacitorTwilioVoice.addListener('callInviteReceived', async (data) => {
   *   if (shouldRejectCall(data.from)) {
   *     await CapacitorTwilioVoice.rejectCall({
   *       callSid: data.callSid
   *     });
   *   }
   * });
   * ```
   */
  rejectCall(options: { callSid: string }): Promise<{ success: boolean }>;

  /**
   * End an active call.
   *
   * If callSid is not provided, this will end the currently active call.
   *
   * @param options - Configuration object
   * @param options.callSid - Unique identifier of the call to end (optional, defaults to current active call)
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * // End the current active call
   * await CapacitorTwilioVoice.endCall({});
   *
   * // End a specific call
   * await CapacitorTwilioVoice.endCall({
   *   callSid: 'CA1234567890abcdef'
   * });
   * ```
   */
  endCall(options: { callSid?: string }): Promise<{ success: boolean }>;

  // Call Controls

  /**
   * Mute or unmute the microphone during an active call.
   *
   * When muted, the other party will not hear audio from your microphone.
   *
   * @param options - Configuration object
   * @param options.muted - Whether to mute (true) or unmute (false) the microphone
   * @param options.callSid - Unique identifier of the call (optional, defaults to current active call)
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * // Mute the microphone
   * await CapacitorTwilioVoice.muteCall({
   *   muted: true
   * });
   *
   * // Unmute the microphone
   * await CapacitorTwilioVoice.muteCall({
   *   muted: false
   * });
   * ```
   */
  muteCall(options: { muted: boolean; callSid?: string }): Promise<{ success: boolean }>;

  /**
   * Enable or disable speakerphone mode.
   *
   * When enabled, audio will be routed through the device's speaker instead of the earpiece.
   *
   * @param options - Configuration object
   * @param options.enabled - Whether to enable (true) or disable (false) speakerphone mode
   * @returns Promise that resolves with success status
   *
   * @example
   * ```typescript
   * // Enable speakerphone
   * await CapacitorTwilioVoice.setSpeaker({
   *   enabled: true
   * });
   *
   * // Disable speakerphone
   * await CapacitorTwilioVoice.setSpeaker({
   *   enabled: false
   * });
   * ```
   */
  setSpeaker(options: { enabled: boolean }): Promise<{ success: boolean }>;

  // Call Status

  /**
   * Get the current status of the active call.
   *
   * This provides real-time information about the call state, mute status,
   * hold status, and call identifiers.
   *
   * @returns Promise that resolves with call status information
   * @returns hasActiveCall - Whether there is currently an active call
   * @returns isOnHold - Whether the call is on hold
   * @returns isMuted - Whether the microphone is muted
   * @returns callSid - Unique identifier of the active call (if any)
   * @returns callState - Current state of the call (e.g., 'connecting', 'connected', 'ringing')
   * @returns pendingInvites - Array of pending incoming call invitations with the same data as callInviteReceived
   * @returns activeCallsCount - Total number of active calls being tracked
   *
   * @example
   * ```typescript
   * const status = await CapacitorTwilioVoice.getCallStatus();
   * if (status.hasActiveCall) {
   *   console.log('Call SID:', status.callSid);
   *   console.log('Call State:', status.callState);
   *   console.log('Is Muted:', status.isMuted);
   *   console.log('Is On Hold:', status.isOnHold);
   * }
   * ```
   */
  getCallStatus(): Promise<{
    /** Whether there is currently an active call */
    hasActiveCall: boolean;
    /** Whether the active call is on hold */
    isOnHold: boolean;
    /** Whether the active call is muted */
    isMuted: boolean;
    /** The unique identifier (SID) for the active call */
    callSid?: string;
    /** Current state: 'idle', 'connecting', 'ringing', 'connected', 'reconnecting', 'disconnected', or 'unknown' */
    callState?: string;
    /** Array of pending incoming call invitations */
    pendingInvites: Array<CallInvite>;
    /** Total number of active calls being tracked */
    activeCallsCount: number;
  }>;

  // Audio Permissions

  /**
   * Check if microphone permission has been granted.
   *
   * This does not request permission, only checks the current permission status.
   *
   * @returns Promise that resolves with permission status
   * @returns granted - Whether microphone permission has been granted
   *
   * @example
   * ```typescript
   * const result = await CapacitorTwilioVoice.checkMicrophonePermission();
   * if (!result.granted) {
   *   console.log('Microphone permission not granted');
   * }
   * ```
   */
  checkMicrophonePermission(): Promise<{ granted: boolean }>;

  /**
   * Request microphone permission from the user.
   *
   * On iOS and Android, this will show the system permission dialog if permission
   * has not been granted yet. If permission was previously denied, the user may need
   * to grant it in system settings.
   *
   * @returns Promise that resolves with permission status
   * @returns granted - Whether microphone permission was granted
   *
   * @example
   * ```typescript
   * const result = await CapacitorTwilioVoice.requestMicrophonePermission();
   * if (result.granted) {
   *   console.log('Microphone permission granted');
   * } else {
   *   console.log('Microphone permission denied');
   * }
   * ```
   */
  requestMicrophonePermission(): Promise<{ granted: boolean }>;

  // Listeners for events

  /**
   * Listen for incoming call invitations.
   *
   * This event is fired when another user or phone number is calling you.
   * You should call acceptCall() or rejectCall() in response.
   *
   * @param eventName - The event name ('callInviteReceived')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data of type {@link CallInvite}
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * const listener = await CapacitorTwilioVoice.addListener(
   *   'callInviteReceived',
   *   (data) => {
   *     console.log('Incoming call from:', data.from);
   *     console.log('Call SID:', data.callSid);
   *     console.log('Custom params:', data.customParams);
   *   }
   * );
   *
   * // Remove listener when no longer needed
   * await listener.remove();
   * ```
   */
  addListener(
    eventName: 'callInviteReceived',
    listenerFunc: (data: CallInvite) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call connected events.
   *
   * This event is fired when a call (incoming or outgoing) has been successfully
   * connected and audio can be heard.
   *
   * @param eventName - The event name ('callConnected')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the connected call
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callConnected', (data) => {
   *   console.log('Call connected:', data.callSid);
   *   // Start call timer, update UI, etc.
   * });
   * ```
   */
  addListener(
    eventName: 'callConnected',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call invite cancellation events.
   *
   * This event is fired when an incoming call invitation is cancelled before being
   * answered, either by the caller hanging up or by the user declining.
   *
   * @param eventName - The event name ('callInviteCancelled')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the cancelled call
   * @param listenerFunc.data.reason - Reason for cancellation ('user_declined' or 'remote_cancelled')
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callInviteCancelled', (data) => {
   *   if (data.reason === 'remote_cancelled') {
   *     console.log('Caller hung up before you answered');
   *   } else {
   *     console.log('You declined the call');
   *   }
   * });
   * ```
   */
  addListener(
    eventName: 'callInviteCancelled',
    listenerFunc: (data: { callSid: string; reason: 'user_declined' | 'remote_cancelled' }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for outgoing call initiation events.
   *
   * This event is fired when an outgoing call is initiated, either from the app
   * or from the system (e.g., CallKit on iOS, Telecom on Android).
   *
   * @param eventName - The event name ('outgoingCallInitiated')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the outgoing call
   * @param listenerFunc.data.to - Phone number or client identifier being called
   * @param listenerFunc.data.source - Source of the call ('app' or 'system')
   * @param listenerFunc.data.displayName - Display name for the recipient (optional)
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('outgoingCallInitiated', (data) => {
   *   console.log('Calling:', data.to);
   *   console.log('Call initiated from:', data.source);
   *   // Update UI to show outgoing call screen
   * });
   * ```
   */
  addListener(
    eventName: 'outgoingCallInitiated',
    listenerFunc: (data: { callSid: string; to: string; source: 'app' | 'system'; displayName?: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for outgoing call failure events.
   *
   * This event is fired when an outgoing call fails to connect due to various reasons
   * such as missing credentials, permission issues, or network problems.
   *
   * @param eventName - The event name ('outgoingCallFailed')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the failed call
   * @param listenerFunc.data.to - Phone number or client identifier that was being called
   * @param listenerFunc.data.reason - Reason for the failure
   * @param listenerFunc.data.displayName - Display name for the recipient (optional)
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('outgoingCallFailed', (data) => {
   *   console.error('Call to', data.to, 'failed:', data.reason);
   *
   *   switch (data.reason) {
   *     case 'missing_access_token':
   *       // User needs to login
   *       break;
   *     case 'microphone_permission_denied':
   *       // Request microphone permission
   *       break;
   *     case 'connection_failed':
   *       // Network issue, retry later
   *       break;
   *   }
   * });
   * ```
   */
  addListener(
    eventName: 'outgoingCallFailed',
    listenerFunc: (data: {
      callSid: string;
      to: string;
      reason:
        | 'missing_access_token'
        | 'connection_failed'
        | 'no_call_details'
        | 'microphone_permission_denied'
        | 'invalid_contact'
        | 'callkit_request_failed'
        | 'unsupported_intent';
      displayName?: string;
    }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call disconnection events.
   *
   * This event is fired when a call ends, either normally or due to an error.
   *
   * @param eventName - The event name ('callDisconnected')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the disconnected call
   * @param listenerFunc.data.error - Error message if the call was disconnected due to an error (optional)
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callDisconnected', (data) => {
   *   console.log('Call ended:', data.callSid);
   *   if (data.error) {
   *     console.error('Call ended with error:', data.error);
   *   }
   *   // Update UI, stop call timer, etc.
   * });
   * ```
   */
  addListener(
    eventName: 'callDisconnected',
    listenerFunc: (data: { callSid: string; error?: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call ringing events.
   *
   * This event is fired when an outgoing call starts ringing on the other end.
   *
   * @param eventName - The event name ('callRinging')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the ringing call
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callRinging', (data) => {
   *   console.log('Call is ringing:', data.callSid);
   *   // Play ringing sound, update UI, etc.
   * });
   * ```
   */
  addListener(
    eventName: 'callRinging',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call reconnecting events.
   *
   * This event is fired when a call loses connection and Twilio is attempting to
   * reconnect. The call is not disconnected yet but audio may be interrupted.
   *
   * @param eventName - The event name ('callReconnecting')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the reconnecting call
   * @param listenerFunc.data.error - Error message describing the connection issue (optional)
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callReconnecting', (data) => {
   *   console.log('Call reconnecting:', data.callSid);
   *   if (data.error) {
   *     console.log('Reconnection reason:', data.error);
   *   }
   *   // Show reconnecting indicator in UI
   * });
   * ```
   */
  addListener(
    eventName: 'callReconnecting',
    listenerFunc: (data: { callSid: string; error?: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call reconnected events.
   *
   * This event is fired when a call successfully reconnects after a connection loss.
   * Audio should resume normally after this event.
   *
   * @param eventName - The event name ('callReconnected')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the reconnected call
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callReconnected', (data) => {
   *   console.log('Call reconnected:', data.callSid);
   *   // Hide reconnecting indicator, resume normal UI
   * });
   * ```
   */
  addListener(
    eventName: 'callReconnected',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for call quality warning events.
   *
   * This event is fired when the call quality changes, providing warnings about
   * potential issues like high jitter, packet loss, or low audio levels.
   *
   * @param eventName - The event name ('callQualityWarningsChanged')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.callSid - Unique identifier for the call
   * @param listenerFunc.data.currentWarnings - Array of current quality warnings
   * @param listenerFunc.data.previousWarnings - Array of previous quality warnings
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('callQualityWarningsChanged', (data) => {
   *   console.log('Call quality warnings:', data.currentWarnings);
   *
   *   if (data.currentWarnings.includes('high-jitter')) {
   *     console.warn('Network jitter detected');
   *   }
   *   if (data.currentWarnings.includes('high-packet-loss')) {
   *     console.warn('Packet loss detected');
   *   }
   * });
   * ```
   */
  addListener(
    eventName: 'callQualityWarningsChanged',
    listenerFunc: (data: { callSid: string; currentWarnings: string[]; previousWarnings: string[] }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for successful registration events.
   *
   * This event is fired when the device successfully registers with Twilio Voice
   * and is ready to make and receive calls. This typically occurs after a successful
   * login with a valid access token.
   *
   * @param eventName - The event name ('registrationSuccess')
   * @param listenerFunc - Callback function to handle the event
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('registrationSuccess', () => {
   *   console.log('Successfully registered with Twilio Voice');
   *   // Update UI to show ready state
   * });
   * ```
   */
  addListener(eventName: 'registrationSuccess', listenerFunc: () => void): Promise<PluginListenerHandle>;

  /**
   * Listen for registration failure events.
   *
   * This event is fired when the device fails to register with Twilio Voice,
   * typically due to an invalid or expired access token, network issues, or
   * Twilio service problems.
   *
   * @param eventName - The event name ('registrationFailure')
   * @param listenerFunc - Callback function to handle the event
   * @param listenerFunc.data - Event data
   * @param listenerFunc.data.error - Error message describing the registration failure
   * @returns Promise that resolves with a listener handle for removing the listener
   *
   * @example
   * ```typescript
   * await CapacitorTwilioVoice.addListener('registrationFailure', (data) => {
   *   console.error('Registration failed:', data.error);
   *   // Re-authenticate user or show error message
   * });
   * ```
   */
  addListener(
    eventName: 'registrationFailure',
    listenerFunc: (data: { error: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Remove all registered event listeners.
   *
   * This is useful for cleanup when your component unmounts or when you want to
   * reset all event handling.
   *
   * @returns Promise that resolves when all listeners have been removed
   *
   * @example
   * ```typescript
   * // In a React component cleanup
   * useEffect(() => {
   *   // Setup listeners...
   *
   *   return () => {
   *     CapacitorTwilioVoice.removeAllListeners();
   *   };
   * }, []);
   * ```
   */
  removeAllListeners(): Promise<void>;

  /**
   * Get the native Capacitor plugin version
   *
   * @returns {Promise<{ version: string }>} a Promise with version for this plugin
   * @throws An error if something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;
}

/**
 * Handle returned by event listener registration.
 *
 * This interface provides a method to remove the registered event listener
 * when it's no longer needed.
 *
 * @example
 * ```typescript
 * const handle = await CapacitorTwilioVoice.addListener('callConnected', (data) => {
 *   console.log('Call connected:', data.callSid);
 * });
 *
 * // Later, remove the listener
 * await handle.remove();
 * ```
 */
export interface PluginListenerHandle {
  /**
   * Remove the registered event listener.
   *
   * After calling this method, the listener callback will no longer be invoked
   * when the event occurs.
   *
   * @returns Promise that resolves when the listener has been removed
   *
   * @example
   * ```typescript
   * const listener = await CapacitorTwilioVoice.addListener('callInviteReceived', handleIncomingCall);
   * // Remove listener when component unmounts or when no longer needed
   * await listener.remove();
   * ```
   */
  remove(): Promise<void>;
}
