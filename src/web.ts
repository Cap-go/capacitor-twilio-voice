import { WebPlugin } from '@capacitor/core';
import { Device, Call } from '@twilio/voice-sdk';

import type { CapacitorTwilioVoicePlugin, CallInvite, AudioDevice } from './definitions';

export class CapacitorTwilioVoiceWeb extends WebPlugin implements CapacitorTwilioVoicePlugin {
  // Twilio SDK instances
  private device: Device | null = null;
  private activeCall: Call | null = null;

  // State tracking
  private activeCalls: Map<string, Call> = new Map();
  private pendingInvites: Map<string, Call> = new Map();
  private accessToken: string | null = null;
  private currentWarnings: Map<string, Set<string>> = new Map();

  // Audio device state
  private selectedOutputDeviceId: string | null = null;

  // ─── Authentication ────────────────────────────────────────────────

  async login(options: { accessToken: string }): Promise<{ success: boolean }> {
    if (this.isTokenExpired(options.accessToken)) {
      throw new Error('Access token is expired');
    }

    this.accessToken = options.accessToken;

    // If device already exists, update token and re-register
    if (this.device) {
      this.device.updateToken(options.accessToken);
      if (this.device.state !== Device.State.Registered) {
        await this.device.register();
      }
      return { success: true };
    }

    // Create new Device
    this.device = new Device(options.accessToken, {
      logLevel: 3,
      codecPreferences: [Call.Codec.Opus, Call.Codec.PCMU],
      closeProtection: true,
      allowIncomingWhileBusy: true,
    });

    // Wire device-level events
    this.wireDeviceEvents(this.device);

    // Register — resolves on 'registered', rejects on 'error'
    return new Promise((resolve, reject) => {
      const onRegistered = () => {
        cleanup();
        resolve({ success: true });
      };
      const onError = (error: unknown) => {
        cleanup();
        reject(error);
      };
      const cleanup = () => {
        this.device?.off('registered', onRegistered);
        this.device?.off('error', onError);
      };
      this.device!.on('registered', onRegistered);
      this.device!.on('error', onError);
      this.device!.register();
    });
  }

  async logout(): Promise<{ success: boolean }> {
    if (!this.device) {
      return { success: true };
    }

    // Disconnect all active calls
    for (const call of this.activeCalls.values()) {
      call.disconnect();
    }

    // Reject all pending invites
    for (const call of this.pendingInvites.values()) {
      call.reject();
    }

    // Unregister and destroy device
    this.device.unregister();
    this.device.destroy();

    // Clear all state
    this.device = null;
    this.activeCall = null;
    this.activeCalls.clear();
    this.pendingInvites.clear();
    this.accessToken = null;
    this.currentWarnings.clear();
    this.selectedOutputDeviceId = null;

    return { success: true };
  }

  async isLoggedIn(): Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string }> {
    const isLoggedIn = this.device !== null && this.device.state === Device.State.Registered;
    const hasValidToken = this.accessToken !== null && !this.isTokenExpired(this.accessToken);
    const identity = this.accessToken ? this.getIdentityFromToken(this.accessToken) : undefined;

    return { isLoggedIn, hasValidToken, identity };
  }

  // ─── Call Management ───────────────────────────────────────────────

  async makeCall(options: { to: string; params?: Record<string, string> }): Promise<{ success: boolean; callSid?: string }> {
    if (!this.device || this.device.state !== Device.State.Registered) {
      this.notifyListeners('outgoingCallFailed', {
        callSid: '',
        to: options.to,
        reason: 'missing_access_token',
      });
      return { success: false };
    }

    // Check microphone permission
    const micPermission = await this.checkMicrophonePermission();
    if (!micPermission.granted) {
      this.notifyListeners('outgoingCallFailed', {
        callSid: '',
        to: options.to,
        reason: 'microphone_permission_denied',
      });
      return { success: false };
    }

    try {
      const connectParams: Record<string, string> = { To: options.to };
      // Pass custom params (displayName, wardName, accessId, etc.) through to Twilio
      if (options.params) {
        Object.assign(connectParams, options.params);
      }
      const call = await this.device.connect({
        params: connectParams,
      });

      const callSid = call.parameters?.CallSid || `web-${Date.now()}`;

      // Wire call events
      this.wireCallEvents(call, callSid);

      // Track call
      this.activeCalls.set(callSid, call);
      this.activeCall = call;

      // Emit outgoingCallInitiated
      const outgoingData = {
        callSid,
        to: options.to,
        source: 'app' as const,
      };
      this.notifyListeners('outgoingCallInitiated', outgoingData);
      this.dispatchFallbackEvent('outgoingCallInitiated', outgoingData);

      return { success: true, callSid };
    } catch {
      this.notifyListeners('outgoingCallFailed', {
        callSid: '',
        to: options.to,
        reason: 'connection_failed',
      });
      return { success: false };
    }
  }

  async acceptCall(options: { callSid: string }): Promise<{ success: boolean }> {
    const call = this.pendingInvites.get(options.callSid);
    if (!call) {
      return { success: false };
    }

    call.accept();

    // Move from pending to active
    this.pendingInvites.delete(options.callSid);
    this.activeCalls.set(options.callSid, call);
    this.activeCall = call;

    return { success: true };
  }

  async rejectCall(options: { callSid: string }): Promise<{ success: boolean }> {
    const call = this.pendingInvites.get(options.callSid);
    if (!call) {
      return { success: false };
    }

    call.reject();

    // Remove from pending and emit cancellation
    this.pendingInvites.delete(options.callSid);
    const rejectData = {
      callSid: options.callSid,
      reason: 'user_declined' as const,
    };
    this.notifyListeners('callInviteCancelled', rejectData);
    this.dispatchFallbackEvent('callInviteCancelled', rejectData);

    return { success: true };
  }

  async endCall(options: { callSid?: string }): Promise<{ success: boolean }> {
    let call: Call | undefined;
    let resolvedCallSid: string | undefined;
    if (options.callSid) {
      call = this.activeCalls.get(options.callSid) || this.pendingInvites.get(options.callSid);
      resolvedCallSid = options.callSid;
    } else {
      call = this.activeCall || undefined;
      resolvedCallSid = call ? this.getCallSid(call) : undefined;
    }

    if (!call) {
      return { success: false };
    }

    // Remove all listeners before disconnecting to prevent the Twilio SDK's
    // internal ICE restart from racing with PeerConnection teardown
    // (causes "Cannot read properties of null (reading 'createOffer')").
    call.removeAllListeners();
    call.disconnect();

    // Trigger cleanup manually since we removed the 'disconnect' listener above
    if (resolvedCallSid) {
      this.handleCallDisconnected(resolvedCallSid);
    }

    return { success: true };
  }

  // ─── Call Controls ─────────────────────────────────────────────────

  async muteCall(options: { muted: boolean; callSid?: string }): Promise<{ success: boolean }> {
    const call = options.callSid ? this.activeCalls.get(options.callSid) : this.activeCall;

    if (!call) {
      return { success: false };
    }

    call.mute(options.muted);
    return { success: true };
  }

  async sendDigits(options: { digits: string; callSid?: string }): Promise<{ success: boolean }> {
    const call = options.callSid ? this.activeCalls.get(options.callSid) : this.activeCall;

    if (!call) {
      return { success: false };
    }

    call.sendDigits(options.digits);
    return { success: true };
  }

  async setSpeaker(options: { enabled: boolean }): Promise<{ success: boolean }> {
    if (!this.device) {
      return { success: false };
    }

    // On web, setSpeaker is a best-effort operation.
    // If output selection is supported (setSinkId API), we can route audio.
    // Otherwise, this is a no-op that returns success (audio goes through default output).
    if (!this.device.audio?.isOutputSelectionSupported) {
      return { success: true };
    }

    try {
      if (options.enabled && this.selectedOutputDeviceId) {
        await this.device.audio.speakerDevices.set([this.selectedOutputDeviceId]);
      } else {
        await this.device.audio.speakerDevices.set(['default']);
      }
      return { success: true };
    } catch {
      return { success: false };
    }
  }

  // ─── Call Status ───────────────────────────────────────────────────

  async getCallStatus(): Promise<{
    hasActiveCall: boolean;
    isOnHold: boolean;
    isMuted: boolean;
    callSid?: string;
    callState?: string;
    pendingInvites: CallInvite[];
    activeCallsCount: number;
  }> {
    const callState = this.activeCall ? this.mapCallStatus(this.activeCall.status()) : undefined;
    const callSid = this.activeCall ? this.getCallSid(this.activeCall) : undefined;

    const pendingInvites: CallInvite[] = Array.from(this.pendingInvites.entries()).map(([sid, call]) => ({
      callSid: sid,
      from: call.parameters?.From || '',
      to: call.parameters?.To || '',
      customParams: this.callCustomParamsToRecord(call.customParameters),
    }));

    return {
      hasActiveCall: this.activeCall !== null,
      isOnHold: false, // Twilio JS SDK does not have hold — always false
      isMuted: this.activeCall?.isMuted() ?? false,
      callSid,
      callState,
      pendingInvites,
      activeCallsCount: this.activeCalls.size,
    };
  }

  // ─── Audio Permissions ─────────────────────────────────────────────

  async checkMicrophonePermission(): Promise<{ granted: boolean }> {
    try {
      if (navigator.permissions) {
        const result = await navigator.permissions.query({
          name: 'microphone' as PermissionName,
        });
        return { granted: result.state === 'granted' };
      }
      // Fallback: check if device labels are available (implies permission was granted)
      const devices = await navigator.mediaDevices.enumerateDevices();
      const hasLabels = devices.some((d) => d.kind === 'audioinput' && d.label !== '');
      return { granted: hasLabels };
    } catch {
      return { granted: false };
    }
  }

  async requestMicrophonePermission(): Promise<{ granted: boolean }> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      // Stop all tracks immediately — we only needed the permission prompt
      stream.getTracks().forEach((track) => track.stop());
      return { granted: true };
    } catch {
      return { granted: false };
    }
  }

  // ─── Audio Device Selection ────────────────────────────────────────

  async getAudioDevices(): Promise<{ inputs: AudioDevice[]; outputs: AudioDevice[] }> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const inputs: AudioDevice[] = devices
        .filter((d) => d.kind === 'audioinput')
        .map((d) => ({
          deviceId: d.deviceId,
          label: d.label || `Microphone ${d.deviceId.slice(0, 8)}`,
          kind: 'audioinput' as const,
        }));
      const outputs: AudioDevice[] = devices
        .filter((d) => d.kind === 'audiooutput')
        .map((d) => ({
          deviceId: d.deviceId,
          label: d.label || `Speaker ${d.deviceId.slice(0, 8)}`,
          kind: 'audiooutput' as const,
        }));
      return { inputs, outputs };
    } catch {
      return { inputs: [], outputs: [] };
    }
  }

  async setInputDevice(options: { deviceId: string }): Promise<{ success: boolean }> {
    if (!this.device?.audio) {
      return { success: false };
    }
    try {
      await this.device.audio.setInputDevice(options.deviceId);
      return { success: true };
    } catch {
      return { success: false };
    }
  }

  async setOutputDevice(options: { deviceId: string }): Promise<{ success: boolean }> {
    if (!this.device?.audio) {
      return { success: false };
    }
    if (!this.device.audio.isOutputSelectionSupported) {
      return { success: false };
    }
    try {
      this.selectedOutputDeviceId = options.deviceId;
      await this.device.audio.speakerDevices.set([options.deviceId]);
      await this.device.audio.ringtoneDevices.set([options.deviceId]);
      return { success: true };
    } catch {
      return { success: false };
    }
  }

  // ─── Plugin Version ────────────────────────────────────────────────

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: 'web-8.0.17' };
  }

  // ─── Private: Event Wiring ─────────────────────────────────────────

  private wireDeviceEvents(device: Device): void {
    // Registration success
    device.on('registered', () => {
      this.notifyListeners('registrationSuccess', {});
    });

    // Registration/general errors
    device.on('error', (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      this.notifyListeners('registrationFailure', {
        error: message,
      });
    });

    // Incoming call
    device.on('incoming', (call: Call) => {
      const callSid = call.parameters?.CallSid || `incoming-${Date.now()}`;

      // Wire call events
      this.wireCallEvents(call, callSid);

      // Track as pending invite
      this.pendingInvites.set(callSid, call);

      // Extract caller info
      const from = call.parameters?.From || '';
      const to = call.parameters?.To || '';
      let customParams = this.callCustomParamsToRecord(call.customParameters);

      // On web, Twilio puts custom TwiML params in parameters.Params as a
      // URL-encoded string (e.g. "displayName=%2B47...&CapacitorTwilioCallerName=...").
      // The native iOS/Android SDK parses these into customParameters automatically,
      // but the JS SDK does not — we parse them here for parity.
      if (call.parameters?.Params && Object.keys(customParams).length === 0) {
        try {
          const parsed = new URLSearchParams(call.parameters.Params);
          parsed.forEach((value, key) => {
            customParams[key] = value;
          });
        } catch {
          // Ignore parse errors — fall through with empty customParams
        }
      }

      // Emit callInviteReceived via Capacitor event system + window fallback
      const payload = {
        callSid,
        from,
        to,
        customParams,
      };
      this.notifyListeners('callInviteReceived', payload);
      this.dispatchFallbackEvent('callInviteReceived', payload);
    });

    // Listen for device audio changes
    if (device.audio) {
      device.audio.on('deviceChange', () => {
        this.emitAudioDevicesChanged();
      });
    }
  }

  private wireCallEvents(call: Call, callSid: string): void {
    // Call accepted/connected
    call.on('accept', () => {
      const data = { callSid };
      this.notifyListeners('callConnected', data);
      this.dispatchFallbackEvent('callConnected', data);
    });

    // Call disconnected
    call.on('disconnect', () => {
      this.handleCallDisconnected(callSid);
    });

    // Call ringing (outgoing)
    call.on('ringing', () => {
      const data = { callSid };
      this.notifyListeners('callRinging', data);
      this.dispatchFallbackEvent('callRinging', data);
    });

    // Reconnecting
    call.on('reconnecting', (error: unknown) => {
      const message = error instanceof Error ? error.message : undefined;
      const data = { callSid, error: message };
      this.notifyListeners('callReconnecting', data);
      this.dispatchFallbackEvent('callReconnecting', data);
    });

    // Reconnected
    call.on('reconnected', () => {
      const data = { callSid };
      this.notifyListeners('callReconnected', data);
      this.dispatchFallbackEvent('callReconnected', data);
    });

    // Cancel (incoming call cancelled by caller)
    call.on('cancel', () => {
      this.pendingInvites.delete(callSid);
      const data = { callSid, reason: 'remote_cancelled' as const };
      this.notifyListeners('callInviteCancelled', data);
      this.dispatchFallbackEvent('callInviteCancelled', data);
    });

    // Error on call
    call.on('error', () => {
      // If this is an outgoing call that hasn't connected, emit outgoingCallFailed
      if (call.direction === 'OUTGOING' && call.status() !== 'open') {
        const data = {
          callSid,
          to: call.parameters?.To || '',
          reason: 'connection_failed' as const,
        };
        this.notifyListeners('outgoingCallFailed', data);
        this.dispatchFallbackEvent('outgoingCallFailed', data);
      }
    });

    // Quality warnings
    call.on('warning', (warningName: string) => {
      if (!this.currentWarnings.has(callSid)) {
        this.currentWarnings.set(callSid, new Set());
      }
      const warnings = this.currentWarnings.get(callSid)!;
      const previousWarnings = Array.from(warnings);
      warnings.add(warningName);

      this.notifyListeners('callQualityWarningsChanged', {
        callSid,
        currentWarnings: Array.from(warnings),
        previousWarnings,
      });
    });

    call.on('warning-cleared', (warningName: string) => {
      const warnings = this.currentWarnings.get(callSid);
      if (warnings) {
        const previousWarnings = Array.from(warnings);
        warnings.delete(warningName);

        this.notifyListeners('callQualityWarningsChanged', {
          callSid,
          currentWarnings: Array.from(warnings),
          previousWarnings,
        });
      }
    });
  }

  private handleCallDisconnected(callSid: string): void {
    const call = this.activeCalls.get(callSid);

    // Remove from tracking
    this.activeCalls.delete(callSid);
    this.pendingInvites.delete(callSid);
    this.currentWarnings.delete(callSid);

    // Clean up mic if no more active calls
    if (this.activeCalls.size === 0 && this.device?.audio) {
      try {
        this.device.audio.unsetInputDevice();
      } catch {
        // Ignore — may already be unset
      }
    }

    // Update activeCall reference
    if (this.activeCall === call) {
      const remaining = Array.from(this.activeCalls.values());
      this.activeCall = remaining.length > 0 ? remaining[remaining.length - 1] : null;
    }

    // Emit event
    const data = { callSid };
    this.notifyListeners('callDisconnected', data);
    this.dispatchFallbackEvent('callDisconnected', data);
  }

  // ─── Private: Helpers ──────────────────────────────────────────────

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp ? payload.exp < Date.now() / 1000 : false;
    } catch {
      return true;
    }
  }

  private getIdentityFromToken(token: string): string | undefined {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.grants?.identity;
    } catch {
      return undefined;
    }
  }

  private getCallSid(call: Call): string {
    if (call.parameters?.CallSid) {
      return call.parameters.CallSid;
    }
    for (const [sid, c] of this.activeCalls.entries()) {
      if (c === call) return sid;
    }
    for (const [sid, c] of this.pendingInvites.entries()) {
      if (c === call) return sid;
    }
    return 'unknown';
  }

  private mapCallStatus(status: string): string {
    switch (status) {
      case 'pending':
        return 'connecting';
      case 'connecting':
        return 'connecting';
      case 'ringing':
        return 'ringing';
      case 'open':
        return 'connected';
      case 'closed':
        return 'disconnected';
      default:
        return 'unknown';
    }
  }

  private callCustomParamsToRecord(params: Map<string, string>): Record<string, string> {
    const record: Record<string, string> = {};
    params.forEach((value, key) => {
      record[key] = value;
    });
    return record;
  }

  private async emitAudioDevicesChanged(): Promise<void> {
    const { inputs, outputs } = await this.getAudioDevices();
    this.notifyListeners('audioDevicesChanged', { inputs, outputs });
  }

  /**
   * Dispatch a window CustomEvent as a fallback for Capacitor proxy listener
   * registration bug where only the first addListener call succeeds.
   * useTwilioVoice.ts listens for these events as a backup delivery path.
   */
  private dispatchFallbackEvent(eventName: string, data: any): void {
    try {
      window.dispatchEvent(new CustomEvent(`capacitor-twilio-${eventName}`, { detail: data }));
    } catch (e) {
      console.warn(`[CapacitorTwilioVoiceWeb] Failed to dispatch fallback event '${eventName}':`, e);
    }
  }
}
