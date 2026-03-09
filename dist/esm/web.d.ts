import { WebPlugin } from '@capacitor/core';
import type { CapacitorTwilioVoicePlugin, CallInvite, AudioDevice } from './definitions';
export declare class CapacitorTwilioVoiceWeb extends WebPlugin implements CapacitorTwilioVoicePlugin {
    private device;
    private activeCall;
    private activeCalls;
    private pendingInvites;
    private accessToken;
    private currentWarnings;
    private selectedOutputDeviceId;
    private static readonly PLUGIN_BUILD;
    login(options: {
        accessToken: string;
    }): Promise<{
        success: boolean;
    }>;
    logout(): Promise<{
        success: boolean;
    }>;
    isLoggedIn(): Promise<{
        isLoggedIn: boolean;
        hasValidToken: boolean;
        identity?: string;
    }>;
    makeCall(options: {
        to: string;
        params?: Record<string, string>;
    }): Promise<{
        success: boolean;
        callSid?: string;
    }>;
    acceptCall(options: {
        callSid: string;
    }): Promise<{
        success: boolean;
    }>;
    rejectCall(options: {
        callSid: string;
    }): Promise<{
        success: boolean;
    }>;
    endCall(options: {
        callSid?: string;
    }): Promise<{
        success: boolean;
    }>;
    muteCall(options: {
        muted: boolean;
        callSid?: string;
    }): Promise<{
        success: boolean;
    }>;
    sendDigits(options: {
        digits: string;
        callSid?: string;
    }): Promise<{
        success: boolean;
    }>;
    setSpeaker(options: {
        enabled: boolean;
    }): Promise<{
        success: boolean;
    }>;
    getCallStatus(): Promise<{
        hasActiveCall: boolean;
        isOnHold: boolean;
        isMuted: boolean;
        callSid?: string;
        callState?: string;
        pendingInvites: CallInvite[];
        activeCallsCount: number;
    }>;
    checkMicrophonePermission(): Promise<{
        granted: boolean;
    }>;
    requestMicrophonePermission(): Promise<{
        granted: boolean;
    }>;
    getAudioDevices(): Promise<{
        inputs: AudioDevice[];
        outputs: AudioDevice[];
    }>;
    setInputDevice(options: {
        deviceId: string;
    }): Promise<{
        success: boolean;
    }>;
    setOutputDevice(options: {
        deviceId: string;
    }): Promise<{
        success: boolean;
    }>;
    getPluginVersion(): Promise<{
        version: string;
    }>;
    /**
     * Aggressively kill a call and break any ICE restart loops.
     *
     * The Twilio SDK has an internal loop driven by direct property callbacks
     * and a backoff timer — not by EventEmitter listeners. This method breaks
     * the loop at every level:
     *   1. Reset/remove the backoff timer that schedules iceRestart
     *   2. Null out _mediaHandler property callbacks (not EventEmitter)
     *   3. Null out raw RTCPeerConnection event handlers and close the PC
     *   4. Remove EventEmitter + pstream listeners
     *   5. Attempt normal disconnect (best-effort)
     *   6. Emit disconnected event for UI update
     */
    private forceKillCall;
    private wireDeviceEvents;
    private wireCallEvents;
    private handleCallDisconnected;
    private isTokenExpired;
    private getIdentityFromToken;
    private getCallSid;
    private mapCallStatus;
    private callCustomParamsToRecord;
    private emitAudioDevicesChanged;
    /**
     * Dispatch a window CustomEvent as a fallback for Capacitor proxy listener
     * registration bug where only the first addListener call succeeds.
     * useTwilioVoice.ts listens for these events as a backup delivery path.
     */
    private dispatchFallbackEvent;
}
