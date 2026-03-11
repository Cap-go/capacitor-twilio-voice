'use strict';

var core = require('@capacitor/core');
var voiceSdk = require('@twilio/voice-sdk');

const CapacitorTwilioVoice = core.registerPlugin('CapacitorTwilioVoice', {
    web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.CapacitorTwilioVoiceWeb()),
});

class CapacitorTwilioVoiceWeb extends core.WebPlugin {
    constructor() {
        super(...arguments);
        this.device = null;
        this.activeCall = null;
        this.activeCalls = new Map();
        this.pendingInvites = new Map();
        this.accessToken = null;
        this.currentWarnings = new Map();
        this.selectedOutputDeviceId = null;
    }
    // ─── Authentication ────────────────────────────────────────────────
    async login(options) {
        if (this.isTokenExpired(options.accessToken)) {
            throw new Error('Access token is expired');
        }
        this.accessToken = options.accessToken;
        if (this.device) {
            this.device.updateToken(options.accessToken);
            if (this.device.state !== voiceSdk.Device.State.Registered) {
                await this.device.register();
            }
            return { success: true };
        }
        // Disable AudioContext-based sounds to avoid autoplay policy warnings
        // when Device is created before a user gesture (which happens during
        // automatic login on page load). The browser blocks AudioContext.play()
        // before interaction, and the SDK's Sound constructor fires play()
        // during Device construction — leading to 15+ console warnings.
        this.device = new voiceSdk.Device(options.accessToken, {
            logLevel: 3,
            codecPreferences: [voiceSdk.Call.Codec.Opus, voiceSdk.Call.Codec.PCMU],
            closeProtection: true,
            allowIncomingWhileBusy: true,
            disableAudioContextSounds: true,
        });
        this.wireDeviceEvents(this.device);
        return new Promise((resolve, reject) => {
            const onRegistered = () => {
                cleanup();
                resolve({ success: true });
            };
            const onError = (error) => {
                cleanup();
                reject(error);
            };
            const cleanup = () => {
                var _a, _b;
                (_a = this.device) === null || _a === void 0 ? void 0 : _a.off('registered', onRegistered);
                (_b = this.device) === null || _b === void 0 ? void 0 : _b.off('error', onError);
            };
            this.device.on('registered', onRegistered);
            this.device.on('error', onError);
            this.device.register();
        });
    }
    async logout() {
        if (!this.device) {
            return { success: true };
        }
        this.device.disconnectAll();
        for (const call of this.pendingInvites.values()) {
            call.reject();
        }
        this.device.unregister();
        this.device.destroy();
        this.device = null;
        this.activeCall = null;
        this.activeCalls.clear();
        this.pendingInvites.clear();
        this.accessToken = null;
        this.currentWarnings.clear();
        this.selectedOutputDeviceId = null;
        return { success: true };
    }
    async isLoggedIn() {
        const isLoggedIn = this.device !== null && this.device.state === voiceSdk.Device.State.Registered;
        const hasValidToken = this.accessToken !== null && !this.isTokenExpired(this.accessToken);
        const identity = this.accessToken ? this.getIdentityFromToken(this.accessToken) : undefined;
        return { isLoggedIn, hasValidToken, identity };
    }
    // ─── Call Management ───────────────────────────────────────────────
    async makeCall(options) {
        var _a;
        if (!this.device || this.device.state !== voiceSdk.Device.State.Registered) {
            const data = {
                callSid: '',
                to: options.to,
                reason: 'missing_access_token',
            };
            this.notifyListeners('outgoingCallFailed', data);
            this.dispatchFallbackEvent('outgoingCallFailed', data);
            return { success: false };
        }
        let micPermission = await this.checkMicrophonePermission();
        if (!micPermission.granted) {
            micPermission = await this.requestMicrophonePermission();
        }
        if (!micPermission.granted) {
            const micData = {
                callSid: '',
                to: options.to,
                reason: 'microphone_permission_denied',
            };
            this.notifyListeners('outgoingCallFailed', micData);
            this.dispatchFallbackEvent('outgoingCallFailed', micData);
            return { success: false };
        }
        try {
            const connectParams = { To: options.to };
            if (options.params) {
                Object.assign(connectParams, options.params);
            }
            const call = await this.device.connect({
                params: connectParams,
            });
            const callSid = ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.CallSid) || `web-${Date.now()}`;
            this.wireCallEvents(call, callSid);
            this.activeCalls.set(callSid, call);
            this.activeCall = call;
            const outgoingData = {
                callSid,
                to: options.to,
                source: 'app',
            };
            this.notifyListeners('outgoingCallInitiated', outgoingData);
            this.dispatchFallbackEvent('outgoingCallInitiated', outgoingData);
            return { success: true, callSid };
        }
        catch (_b) {
            const failData = {
                callSid: '',
                to: options.to,
                reason: 'connection_failed',
            };
            this.notifyListeners('outgoingCallFailed', failData);
            this.dispatchFallbackEvent('outgoingCallFailed', failData);
            return { success: false };
        }
    }
    async acceptCall(options) {
        const call = this.pendingInvites.get(options.callSid);
        if (!call) {
            return { success: false };
        }
        call.accept();
        this.pendingInvites.delete(options.callSid);
        this.activeCalls.set(options.callSid, call);
        this.activeCall = call;
        return { success: true };
    }
    async rejectCall(options) {
        const call = this.pendingInvites.get(options.callSid);
        if (!call) {
            return { success: false };
        }
        call.reject();
        this.pendingInvites.delete(options.callSid);
        const rejectData = {
            callSid: options.callSid,
            reason: 'user_declined',
        };
        this.notifyListeners('callInviteCancelled', rejectData);
        this.dispatchFallbackEvent('callInviteCancelled', rejectData);
        return { success: true };
    }
    async endCall(options) {
        var _a;
        let call;
        let resolvedCallSid;
        if (options.callSid) {
            call = this.activeCalls.get(options.callSid) || this.pendingInvites.get(options.callSid);
            resolvedCallSid = options.callSid;
        }
        else {
            call = this.activeCall || undefined;
            resolvedCallSid = call ? this.getCallSid(call) : undefined;
        }
        if (!call) {
            return { success: false };
        }
        console.log(`[TwilioVoiceWeb] endCall: ${resolvedCallSid}, status=${call.status()}`);
        // Phase 1: Graceful disconnect — sends hangup to Twilio servers via PStream.
        // This MUST happen before any cleanup because call._disconnect() checks
        // pstream.status !== 'disconnected' before sending the hangup message.
        // If we tear down the pstream/mediaHandler first, the hangup never reaches
        // Twilio and the remote party's call continues indefinitely.
        let gracefulDone = false;
        try {
            call.disconnect();
            gracefulDone = true;
        }
        catch (_b) {
            /* public API may throw if call is in wrong state */
        }
        if (!gracefulDone) {
            try {
                const c = call;
                const hasPstream = c._pstream && typeof c._pstream.status === 'string' && typeof c._pstream.hangup === 'function';
                const callSidForHangup = ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.CallSid) || c.outboundConnectionId;
                if (hasPstream && callSidForHangup && c._pstream.status !== 'disconnected') {
                    c._pstream.hangup(callSidForHangup, null);
                    gracefulDone = true;
                }
            }
            catch (_c) {
                /* best effort — internals may not match expected shape */
            }
        }
        // Phase 2: Hard cleanup — break ICE restart loops and tear down WebRTC.
        // Schedule after a short delay to let the hangup message flush through
        // the WebSocket, or run immediately if graceful disconnect failed.
        const runHardCleanup = () => {
            this.hardCleanupCall(call, resolvedCallSid);
        };
        if (gracefulDone) {
            setTimeout(runHardCleanup, CapacitorTwilioVoiceWeb.HARD_CLEANUP_TIMEOUT_MS);
        }
        else {
            runHardCleanup();
            if (resolvedCallSid) {
                this.handleCallDisconnected(resolvedCallSid);
            }
        }
        return { success: true };
    }
    // ─── Call Controls ─────────────────────────────────────────────────
    async muteCall(options) {
        const call = options.callSid ? this.activeCalls.get(options.callSid) : this.activeCall;
        if (!call) {
            return { success: false };
        }
        call.mute(options.muted);
        return { success: true };
    }
    async sendDigits(options) {
        const call = options.callSid ? this.activeCalls.get(options.callSid) : this.activeCall;
        if (!call) {
            return { success: false };
        }
        call.sendDigits(options.digits);
        return { success: true };
    }
    async setSpeaker(options) {
        var _a;
        if (!this.device) {
            return { success: false };
        }
        if (!((_a = this.device.audio) === null || _a === void 0 ? void 0 : _a.isOutputSelectionSupported)) {
            return { success: true };
        }
        try {
            if (options.enabled && this.selectedOutputDeviceId) {
                await this.device.audio.speakerDevices.set([this.selectedOutputDeviceId]);
            }
            else {
                await this.device.audio.speakerDevices.set(['default']);
            }
            return { success: true };
        }
        catch (_b) {
            return { success: false };
        }
    }
    // ─── Call Status ───────────────────────────────────────────────────
    async getCallStatus() {
        var _a, _b;
        const callState = this.activeCall ? this.mapCallStatus(this.activeCall.status()) : undefined;
        const callSid = this.activeCall ? this.getCallSid(this.activeCall) : undefined;
        const pendingInvites = Array.from(this.pendingInvites.entries()).map(([sid, call]) => {
            var _a, _b;
            return ({
                callSid: sid,
                from: ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.From) || '',
                to: ((_b = call.parameters) === null || _b === void 0 ? void 0 : _b.To) || '',
                customParams: this.callCustomParamsToRecord(call.customParameters),
            });
        });
        return {
            hasActiveCall: this.activeCall !== null,
            isOnHold: false,
            isMuted: (_b = (_a = this.activeCall) === null || _a === void 0 ? void 0 : _a.isMuted()) !== null && _b !== void 0 ? _b : false,
            callSid,
            callState,
            pendingInvites,
            activeCallsCount: this.activeCalls.size,
        };
    }
    // ─── Audio Permissions ─────────────────────────────────────────────
    async checkMicrophonePermission() {
        try {
            if (navigator.permissions) {
                const result = await navigator.permissions.query({
                    name: 'microphone',
                });
                return { granted: result.state === 'granted' };
            }
            const devices = await navigator.mediaDevices.enumerateDevices();
            const hasLabels = devices.some((d) => d.kind === 'audioinput' && d.label !== '');
            return { granted: hasLabels };
        }
        catch (_a) {
            return { granted: false };
        }
    }
    async requestMicrophonePermission() {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            stream.getTracks().forEach((track) => {
                track.stop();
            });
            return { granted: true };
        }
        catch (_a) {
            return { granted: false };
        }
    }
    // ─── Audio Device Selection ────────────────────────────────────────
    async getAudioDevices() {
        try {
            const devices = await navigator.mediaDevices.enumerateDevices();
            const inputs = devices
                .filter((d) => d.kind === 'audioinput')
                .map((d) => ({
                deviceId: d.deviceId,
                label: d.label || `Microphone ${d.deviceId.slice(0, 8)}`,
                kind: 'audioinput',
            }));
            const outputs = devices
                .filter((d) => d.kind === 'audiooutput')
                .map((d) => ({
                deviceId: d.deviceId,
                label: d.label || `Speaker ${d.deviceId.slice(0, 8)}`,
                kind: 'audiooutput',
            }));
            return { inputs, outputs };
        }
        catch (_a) {
            return { inputs: [], outputs: [] };
        }
    }
    async setInputDevice(options) {
        var _a;
        if (!((_a = this.device) === null || _a === void 0 ? void 0 : _a.audio)) {
            return { success: false };
        }
        try {
            await this.device.audio.setInputDevice(options.deviceId);
            return { success: true };
        }
        catch (_b) {
            return { success: false };
        }
    }
    async setOutputDevice(options) {
        var _a;
        if (!((_a = this.device) === null || _a === void 0 ? void 0 : _a.audio)) {
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
        }
        catch (_b) {
            return { success: false };
        }
    }
    // ─── Plugin Version ────────────────────────────────────────────────
    async getPluginVersion() {
        return { version: '8.0.28' };
    }
    // ─── Private: Call Cleanup ──────────────────────────────────────────
    /**
     * Tear down a Call's internal WebRTC and backoff machinery to break ICE
     * restart loops. This is the "hard" phase — only run AFTER the graceful
     * disconnect has had time to send the hangup message via PStream.
     *
     * The SDK's ICE loop is driven by direct property callbacks and a backoff
     * timer (not EventEmitter listeners), so removeAllListeners() alone is
     * insufficient. We must:
     *   1. Reset the backoff timer that schedules iceRestart()
     *   2. Null out _mediaHandler property callbacks
     *   3. Close the RTCPeerConnection
     *   4. Remove EventEmitter + PStream listeners
     */
    hardCleanupCall(call, callSid) {
        var _a;
        const c = call;
        try {
            if (c._mediaReconnectBackoff && typeof c._mediaReconnectBackoff.reset === 'function') {
                c._mediaReconnectBackoff.reset();
                if (typeof c._mediaReconnectBackoff.removeAllListeners === 'function') {
                    c._mediaReconnectBackoff.removeAllListeners();
                }
            }
        }
        catch (_b) {
            /* best effort */
        }
        try {
            const mh = c._mediaHandler;
            if (mh && typeof mh === 'object') {
                const noop = () => undefined;
                mh.onicegatheringfailure = noop;
                mh.onicegatheringstatechange = noop;
                mh.ondisconnected = noop;
                mh.onfailed = noop;
                mh.onconnected = noop;
                mh.onreconnected = noop;
                mh.onerror = noop;
                mh.onclose = noop;
                mh.onopen = noop;
            }
        }
        catch (_c) {
            /* best effort */
        }
        try {
            const mh = c._mediaHandler;
            if (mh && typeof mh === 'object') {
                if ((_a = mh.version) === null || _a === void 0 ? void 0 : _a.pc) {
                    mh.version.pc.onicegatheringstatechange = null;
                    mh.version.pc.oniceconnectionstatechange = null;
                    mh.version.pc.onconnectionstatechange = null;
                    mh.version.pc.onicecandidate = null;
                }
                if (typeof mh.close === 'function') {
                    mh.close();
                }
            }
        }
        catch (_d) {
            /* best effort */
        }
        try {
            call.removeAllListeners();
        }
        catch (_e) {
            /* best effort */
        }
        try {
            if (typeof c._cleanupEventListeners === 'function')
                c._cleanupEventListeners();
        }
        catch (_f) {
            /* best effort */
        }
        console.log(`[TwilioVoiceWeb] hardCleanupCall done: ${callSid}`);
    }
    // ─── Private: Event Wiring ─────────────────────────────────────────
    wireDeviceEvents(device) {
        device.on('registered', () => {
            this.notifyListeners('registrationSuccess', {});
            this.dispatchFallbackEvent('registrationSuccess', {});
        });
        device.on('error', (error) => {
            const message = error instanceof Error ? error.message : String(error);
            console.warn('[TwilioVoiceWeb] Device error:', message);
            this.notifyListeners('registrationFailure', { error: message });
            this.dispatchFallbackEvent('registrationFailure', { error: message });
        });
        device.on('incoming', (call) => {
            var _a, _b, _c, _d;
            const callSid = ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.CallSid) || `incoming-${Date.now()}`;
            this.wireCallEvents(call, callSid);
            this.pendingInvites.set(callSid, call);
            const from = ((_b = call.parameters) === null || _b === void 0 ? void 0 : _b.From) || '';
            const to = ((_c = call.parameters) === null || _c === void 0 ? void 0 : _c.To) || '';
            const customParams = this.callCustomParamsToRecord(call.customParameters);
            // The JS SDK doesn't parse TwiML Params the way native SDKs do.
            // Parse the URL-encoded string for parity with iOS/Android.
            if (((_d = call.parameters) === null || _d === void 0 ? void 0 : _d.Params) && Object.keys(customParams).length === 0) {
                try {
                    const parsed = new URLSearchParams(call.parameters.Params);
                    parsed.forEach((value, key) => {
                        customParams[key] = value;
                    });
                }
                catch (_e) {
                    /* ignore parse errors */
                }
            }
            const payload = { callSid, from, to, customParams };
            this.notifyListeners('callInviteReceived', payload);
            this.dispatchFallbackEvent('callInviteReceived', payload);
        });
        if (device.audio) {
            device.audio.on('deviceChange', () => {
                this.emitAudioDevicesChanged();
            });
        }
    }
    wireCallEvents(call, callSid) {
        let currentSid = callSid;
        let rekeyed = false;
        const rekeyIfNeeded = () => {
            var _a;
            if (rekeyed)
                return;
            const realSid = (_a = call.parameters) === null || _a === void 0 ? void 0 : _a.CallSid;
            if (realSid && realSid !== currentSid) {
                const existing = this.activeCalls.get(currentSid);
                if (existing === call) {
                    this.activeCalls.delete(currentSid);
                    this.activeCalls.set(realSid, call);
                }
                currentSid = realSid;
                rekeyed = true;
            }
        };
        call.on('accept', () => {
            rekeyIfNeeded();
            const data = { callSid: currentSid };
            this.notifyListeners('callConnected', data);
            this.dispatchFallbackEvent('callConnected', data);
        });
        call.on('disconnect', () => {
            rekeyIfNeeded();
            this.handleCallDisconnected(currentSid);
        });
        call.on('ringing', () => {
            rekeyIfNeeded();
            const data = { callSid: currentSid };
            this.notifyListeners('callRinging', data);
            this.dispatchFallbackEvent('callRinging', data);
        });
        call.on('reconnecting', (error) => {
            rekeyIfNeeded();
            const message = error instanceof Error ? error.message : undefined;
            const data = { callSid: currentSid, error: message };
            this.notifyListeners('callReconnecting', data);
            this.dispatchFallbackEvent('callReconnecting', data);
        });
        call.on('reconnected', () => {
            rekeyIfNeeded();
            const data = { callSid: currentSid };
            this.notifyListeners('callReconnected', data);
            this.dispatchFallbackEvent('callReconnected', data);
        });
        call.on('cancel', () => {
            rekeyIfNeeded();
            this.pendingInvites.delete(currentSid);
            const data = { callSid: currentSid, reason: 'remote_cancelled' };
            this.notifyListeners('callInviteCancelled', data);
            this.dispatchFallbackEvent('callInviteCancelled', data);
        });
        call.on('error', (error) => {
            var _a;
            rekeyIfNeeded();
            const message = error instanceof Error ? error.message : String(error);
            const code = error === null || error === void 0 ? void 0 : error.code;
            console.warn(`[TwilioVoiceWeb] Call error (${currentSid}): code=${code} ${message}`);
            if (call.direction === 'OUTGOING' && call.status() !== 'open') {
                const data = {
                    callSid: currentSid,
                    to: ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.To) || '',
                    reason: 'connection_failed',
                };
                this.notifyListeners('outgoingCallFailed', data);
                this.dispatchFallbackEvent('outgoingCallFailed', data);
            }
        });
        call.on('warning', (warningName) => {
            rekeyIfNeeded();
            if (!this.currentWarnings.has(currentSid)) {
                this.currentWarnings.set(currentSid, new Set());
            }
            const warnings = this.currentWarnings.get(currentSid);
            const previousWarnings = Array.from(warnings);
            warnings.add(warningName);
            const data = {
                callSid: currentSid,
                currentWarnings: Array.from(warnings),
                previousWarnings,
            };
            this.notifyListeners('callQualityWarningsChanged', data);
            this.dispatchFallbackEvent('callQualityWarningsChanged', data);
        });
        call.on('warning-cleared', (warningName) => {
            rekeyIfNeeded();
            const warnings = this.currentWarnings.get(currentSid);
            if (warnings) {
                const previousWarnings = Array.from(warnings);
                warnings.delete(warningName);
                const data = {
                    callSid: currentSid,
                    currentWarnings: Array.from(warnings),
                    previousWarnings,
                };
                this.notifyListeners('callQualityWarningsChanged', data);
                this.dispatchFallbackEvent('callQualityWarningsChanged', data);
            }
        });
    }
    handleCallDisconnected(callSid) {
        var _a;
        const call = this.activeCalls.get(callSid);
        this.activeCalls.delete(callSid);
        this.pendingInvites.delete(callSid);
        this.currentWarnings.delete(callSid);
        if (this.activeCalls.size === 0 && ((_a = this.device) === null || _a === void 0 ? void 0 : _a.audio)) {
            try {
                this.device.audio.unsetInputDevice();
            }
            catch (_b) {
                // Ignore — may already be unset
            }
        }
        if (this.activeCall === call) {
            const remaining = Array.from(this.activeCalls.values());
            this.activeCall = remaining.length > 0 ? remaining[remaining.length - 1] : null;
        }
        const data = { callSid };
        this.notifyListeners('callDisconnected', data);
        this.dispatchFallbackEvent('callDisconnected', data);
    }
    // ─── Private: Helpers ──────────────────────────────────────────────
    base64UrlDecode(str) {
        let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
        const pad = base64.length % 4;
        if (pad) {
            base64 += '='.repeat(4 - pad);
        }
        return atob(base64);
    }
    isTokenExpired(token) {
        try {
            const payload = JSON.parse(this.base64UrlDecode(token.split('.')[1]));
            return payload.exp ? payload.exp < Date.now() / 1000 : false;
        }
        catch (_a) {
            return true;
        }
    }
    getIdentityFromToken(token) {
        var _a;
        try {
            const payload = JSON.parse(this.base64UrlDecode(token.split('.')[1]));
            return (_a = payload.grants) === null || _a === void 0 ? void 0 : _a.identity;
        }
        catch (_b) {
            return undefined;
        }
    }
    getCallSid(call) {
        var _a;
        if ((_a = call.parameters) === null || _a === void 0 ? void 0 : _a.CallSid) {
            return call.parameters.CallSid;
        }
        for (const [sid, c] of this.activeCalls.entries()) {
            if (c === call)
                return sid;
        }
        for (const [sid, c] of this.pendingInvites.entries()) {
            if (c === call)
                return sid;
        }
        return 'unknown';
    }
    mapCallStatus(status) {
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
    callCustomParamsToRecord(params) {
        const record = {};
        params.forEach((value, key) => {
            record[key] = value;
        });
        return record;
    }
    async emitAudioDevicesChanged() {
        const { inputs, outputs } = await this.getAudioDevices();
        const data = { inputs, outputs };
        this.notifyListeners('audioDevicesChanged', data);
        this.dispatchFallbackEvent('audioDevicesChanged', data);
    }
    /**
     * Dispatch a window CustomEvent as a fallback for Capacitor proxy listener
     * registration bug where only the first addListener call succeeds.
     * useTwilioVoice.ts listens for these events as a backup delivery path.
     */
    dispatchFallbackEvent(eventName, data) {
        try {
            window.dispatchEvent(new CustomEvent(`capacitor-twilio-${eventName}`, { detail: data }));
        }
        catch (e) {
            console.warn(`[CapacitorTwilioVoiceWeb] Failed to dispatch fallback event '${eventName}':`, e);
        }
    }
}
CapacitorTwilioVoiceWeb.HARD_CLEANUP_TIMEOUT_MS = 500;

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    CapacitorTwilioVoiceWeb: CapacitorTwilioVoiceWeb
});

exports.CapacitorTwilioVoice = CapacitorTwilioVoice;
//# sourceMappingURL=plugin.cjs.js.map
