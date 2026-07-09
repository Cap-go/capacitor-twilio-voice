import { WebPlugin } from '@capacitor/core';

import type { AudioOutputOption, AudioOutputType, CapacitorTwilioVoicePlugin, CallInvite } from './definitions';

export class CapacitorTwilioVoiceWeb extends WebPlugin implements CapacitorTwilioVoicePlugin {
  // Authentication
  async login(_options: { accessToken: string }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async logout(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isLoggedIn(): Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string }> {
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Management
  async makeCall(_options: {
    to: string;
    displayName?: string;
    callerId?: string;
  }): Promise<{ success: boolean; callSid?: string }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async acceptCall(_options: { callSid: string }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async rejectCall(_options: { callSid: string }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async endCall(_options: { callSid?: string }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Controls
  async muteCall(_options: { muted: boolean; callSid?: string }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async setSpeaker(_options: { enabled: boolean }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async setAudioOutput(_options: {
    output: AudioOutputType;
  }): Promise<{ success: boolean; audioOutput: AudioOutputType | null; availableAudioOutputs: AudioOutputOption[] }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async setProximityMonitoring(_options: { enabled: boolean }): Promise<{ success: boolean }> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  async sendDigits(_options: { digits: string; callSid?: string }): Promise<void> {
    void _options;
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Status
  async getCallStatus(): Promise<{
    hasActiveCall: boolean;
    isOnHold: boolean;
    isMuted: boolean;
    callSid?: string;
    callState?: string;
    audioOutput?: AudioOutputType | null;
    availableAudioOutputs: AudioOutputOption[];
    pendingInvites: CallInvite[];
    activeCallsCount: number;
  }> {
    throw this.unimplemented('Not implemented on web.');
  }

  // Audio Permissions
  async checkMicrophonePermission(): Promise<{ granted: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async requestMicrophonePermission(): Promise<{ granted: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: 'web' };
  }
}
