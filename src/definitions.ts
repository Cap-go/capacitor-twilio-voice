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

  // Call Status
  getCallStatus(): Promise<{
    hasActiveCall: boolean;
    isOnHold: boolean;
    isMuted: boolean;
    callSid?: string;
    callState?: string;
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
}

export interface PluginListenerHandle {
  remove(): Promise<void>;
}
