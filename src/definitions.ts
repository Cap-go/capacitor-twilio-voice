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
    listenerFunc: (data: { callSid: string; from: string; to: string }) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'callConnected',
    listenerFunc: (data: { callSid: string }) => void,
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

  /**
   * Get the native Capacitor plugin version
   *
   * @returns {Promise<{ id: string }>} an Promise with version for this device
   * @throws An error if the something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;
}
