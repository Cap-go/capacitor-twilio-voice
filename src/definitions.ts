export interface CapacitorTwilioVoicePlugin {
  // Authentication
  login(options: { accessToken: string }): Promise<{ success: boolean }>;
  logout(): Promise<{ success: boolean }>;
  isLoggedIn(): Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string }>;

  // Call Management
  makeCall(options: { to: string }): Promise<{ success: boolean; callSid?: string }>;
  acceptCall(options: { callSid: string }): Promise<{ success: boolean }>;
  rejectCall(options: { callSid: string }): Promise<{ success: boolean }>;
  endCall(options: { callSid?: string }): Promise<{ success: boolean }>;

  // Call Controls
  muteCall(options: { muted: boolean; callSid?: string }): Promise<{ success: boolean }>;
  setSpeaker(options: { enabled: boolean }): Promise<{ success: boolean }>;

  /**
   * Get the current status of calls in the plugin
   * 
   * @returns Promise with call status information:
   * - hasActiveCall: Whether there is currently an active call
   * - isOnHold: Whether the active call is on hold
   * - isMuted: Whether the active call is muted
   * - callSid: The unique identifier (SID) for the active call, if available
   * - callState: Current state of the call: 'idle' (no call), 'connecting', 'ringing', 'connected', 'reconnecting', 'disconnected', or 'unknown'
   * - pendingInvites: Number of incoming call invitations that haven't been answered or rejected yet
   * - activeCallsCount: Total number of active calls being tracked by the system (may differ from hasActiveCall on some platforms)
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
    /** Number of pending incoming call invitations */
    pendingInvites: number;
    /** Total number of active calls being tracked */
    activeCallsCount: number;
  }>;

  // Audio Permissions
  checkMicrophonePermission(): Promise<{ granted: boolean }>;
  requestMicrophonePermission(): Promise<{ granted: boolean }>;

  // Listeners for events
  addListener(
    eventName: 'callInviteReceived',
    listenerFunc: (data: { callSid: string; from: string; to: string; customParams: Record<string, string> }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callConnected',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callInviteCancelled',
    listenerFunc: (data: { callSid: string; reason: 'user_declined' | 'remote_cancelled' }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'outgoingCallInitiated',
    listenerFunc: (data: { callSid: string; to: string; source: 'app' | 'system'; displayName?: string }) => void,
  ): Promise<PluginListenerHandle>;

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

  addListener(
    eventName: 'callDisconnected',
    listenerFunc: (data: { callSid: string; error?: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callRinging',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callReconnecting',
    listenerFunc: (data: { callSid: string; error?: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callReconnected',
    listenerFunc: (data: { callSid: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callQualityWarningsChanged',
    listenerFunc: (data: { callSid: string; currentWarnings: string[]; previousWarnings: string[] }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(eventName: 'registrationSuccess', listenerFunc: () => void): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'registrationFailure',
    listenerFunc: (data: { error: string }) => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;

  /**
   * Get the native Capacitor plugin version
   *
   * @returns {Promise<{ version: string }>} a Promise with version for this plugin
   * @throws An error if something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;
}

export interface PluginListenerHandle {
  remove(): Promise<void>;
}
