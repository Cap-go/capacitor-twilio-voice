import { WebPlugin } from '@capacitor/core';

import type { CapacitorTwilioVoicePlugin } from './definitions';

export class CapacitorTwilioVoiceWeb extends WebPlugin implements CapacitorTwilioVoicePlugin {
  // Authentication
  async login(_options: { accessToken: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async logout(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isLoggedIn(): Promise<{ isLoggedIn: boolean; hasValidToken: boolean; identity?: string }> {
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Management
  async makeCall(_options: { to: string }): Promise<{ success: boolean; callSid?: string }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async acceptCall(_options: { callSid: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async rejectCall(_options: { callSid: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async endCall(_options: { callSid?: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Controls
  async muteCall(_options: { muted: boolean; callSid?: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setSpeaker(_options: { enabled: boolean }): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  // Call Status
  async getCallStatus(): Promise<{
    hasActiveCall: boolean;
    isOnHold: boolean;
    isMuted: boolean;
    callSid?: string;
    callState?: string;
    pendingInvites: number;
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
