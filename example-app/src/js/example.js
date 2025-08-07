import { CapacitorTwilioVoice } from '@capgo/capacitor-twilio-voice';
import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';

class TwilioVoiceApp {
  constructor() {
    this.currentCallSid = null;
    this.isCallActive = false;
    this.isMuted = false;
    this.isSpeakerOn = false;
    this.isConnecting = false;
    this.isLoggedIn = false;
    this.currentIdentity = null;
    this.pendingCallInvite = null;
    
    this.initializeElements();
    this.setupEventListeners();
    this.setupPluginListeners();
    this.checkLoginStatus();
    this.updateUI();
  }

  initializeElements() {
    this.identityInput = document.getElementById('identityInput');
    this.loginButton = document.getElementById('loginButton');
    this.loginContainer = document.getElementById('loginContainer');
    this.callContainer = document.getElementById('callContainer');
    this.phoneInput = document.getElementById('phoneInput');
    this.callButton = document.getElementById('callButton');
    this.logoutButton = document.getElementById('logoutButton');
    this.callControls = document.getElementById('callControls');
    this.callInfo = document.getElementById('callInfo');
    this.muteSwitch = document.getElementById('muteSwitch');
    this.speakerSwitch = document.getElementById('speakerSwitch');
    this.statusIndicator = document.getElementById('statusIndicator');
    this.qualityWarnings = document.getElementById('qualityWarnings');
    this.twilioLogo = document.getElementById('twilioLogo');
    
    // Incoming call elements
    this.incomingCallScreen = document.getElementById('incomingCallScreen');
    this.callerName = document.getElementById('callerName');
    this.acceptCallBtn = document.getElementById('acceptCallBtn');
    this.rejectCallBtn = document.getElementById('rejectCallBtn');
    
    // User identity element
    this.userIdentity = document.getElementById('userIdentity');
  }

  setupEventListeners() {
    // Login button click
    this.loginButton.addEventListener('click', () => this.handleLogin());
    
    // Enter key in identity input
    this.identityInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        this.identityInput.blur();
        this.handleLogin();
      }
    });
    
    // Call button click
    this.callButton.addEventListener('click', () => this.handleCallButtonClick());
    
    // Logout button click
    this.logoutButton.addEventListener('click', () => this.handleLogout());
    
    // Enter key in phone input
    this.phoneInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        this.phoneInput.blur();
        this.handleCallButtonClick();
      }
    });

    // Mute switch
    this.muteSwitch.addEventListener('click', () => this.toggleMute());
    
    // Speaker switch
    this.speakerSwitch.addEventListener('click', () => this.toggleSpeaker());
    
    // Incoming call buttons
    this.acceptCallBtn.addEventListener('click', () => this.handleAcceptCall());
    this.rejectCallBtn.addEventListener('click', () => this.handleRejectCall());
  }

  async setupPluginListeners() {
    try {
      // Listen for registration events
      await CapacitorTwilioVoice.addListener('registrationSuccess', () => {
        console.log('Successfully registered for VoIP');
        this.showStatus('Registered for calls', 'success');
      });

      await CapacitorTwilioVoice.addListener('registrationFailure', (data) => {
        console.error('Registration failed:', data.error);
        this.showStatus(`Registration failed: ${data.error}`, 'error');
      });

      // Listen for incoming calls
      await CapacitorTwilioVoice.addListener('callInviteReceived', (data) => {
        console.log('Incoming call from:', data.from);
        this.handleIncomingCall(data);
      });

      // Listen for call state changes
      await CapacitorTwilioVoice.addListener('callRinging', (data) => {
        console.log('Call ringing:', data.callSid);
        this.startSpinning();
        this.callButton.textContent = 'Ringing';
        this.callButton.disabled = true;
        this.callInfo.textContent = 'Ringing...';
      });

      await CapacitorTwilioVoice.addListener('callConnected', (data) => {
        console.log('Call connected:', data.callSid);
        this.handleCallConnected(data);
      });

      await CapacitorTwilioVoice.addListener('callDisconnected', (data) => {
        console.log('Call disconnected:', data.callSid);
        this.handleCallDisconnected(data);
      });

      await CapacitorTwilioVoice.addListener('callReconnecting', (data) => {
        console.log('Call reconnecting:', data.callSid);
        this.callButton.textContent = 'Reconnecting';
        this.callButton.disabled = true;
        this.callInfo.textContent = 'Reconnecting...';
        this.showStatus('Call reconnecting...', 'warning');
      });

      await CapacitorTwilioVoice.addListener('callReconnected', (data) => {
        console.log('Call reconnected:', data.callSid);
        this.callButton.textContent = 'Hang Up';
        this.callButton.disabled = false;
        this.callInfo.textContent = 'Connected';
        this.showStatus('Call reconnected', 'success');
      });

      await CapacitorTwilioVoice.addListener('callQualityWarningsChanged', (data) => {
        console.log('Quality warnings changed:', data);
        this.handleQualityWarnings(data);
      });

    } catch (error) {
      console.error('Error setting up plugin listeners:', error);
      this.showStatus('Failed to setup call listeners', 'error');
    }
  }

  async checkLoginStatus() {
    try {
      const result = await CapacitorTwilioVoice.isLoggedIn();
             if (result.isLoggedIn) {
         console.log('User is already logged in with valid token');
                  this.isLoggedIn = true;
          this.currentIdentity = result.identity || 'Unknown';
          this.showStatus(`Already logged in as ${this.currentIdentity}`, 'success');
          this.updateUserIdentityDisplay();
          this.showCallContainer();
       } else {
        console.log('User is not logged in or token expired');
        this.isLoggedIn = false;
        this.showLoginContainer();
      }
    } catch (error) {
      console.error('Error checking login status:', error);
      this.isLoggedIn = false;
      this.showLoginContainer();
    }
  }

  showLoginContainer() {
    this.loginContainer.style.display = 'block';
    this.callContainer.style.display = 'none';
    this.logoutButton.style.display = 'none';
  }

  showCallContainer() {
    this.loginContainer.style.display = 'none';
    this.callContainer.style.display = 'block';
    this.logoutButton.style.display = 'block';
  }

  updateUserIdentityDisplay() {
    if (this.userIdentity) {
      if (this.isLoggedIn && this.currentIdentity) {
        this.userIdentity.textContent = `Logged in as: ${this.currentIdentity}`;
      } else {
        this.userIdentity.textContent = 'Not logged in';
      }
    }
  }

  async checkNotificationPermissions() {
    // Only check on native platforms
    if (Capacitor.getPlatform() === 'web' || Capacitor.getPlatform() === 'ios') {
      return;
    }

    try {
      // Check current permission status
      const permission = await LocalNotifications.checkPermissions();
      console.log('Current notification permission:', permission.display);

      if (permission.display !== 'granted') {
        // Request permission
        console.log('Requesting notification permission...');
        this.showStatus('Requesting notification permission...', 'info');
        
        const result = await LocalNotifications.requestPermissions();
        console.log('Permission request result:', result.display);
        
        if (result.display === 'granted') {
          this.showStatus('Notification permission granted', 'success');
        } else {
          // Permission denied - show warning but continue login
          this.showNotificationPermissionWarning();
        }
      } else {
        console.log('Notification permission already granted');
      }
    } catch (error) {
      console.error('Error checking notification permissions:', error);
      // Don't fail login due to permission issues
      this.showNotificationPermissionWarning();
    }
  }

  showNotificationPermissionWarning() {
    // Create and show warning dialog
    const warningDialog = document.createElement('div');
    warningDialog.innerHTML = `
      <div style="
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.7);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10000;
      ">
        <div style="
          background: white;
          padding: 24px;
          border-radius: 12px;
          max-width: 90%;
          width: 400px;
          text-align: center;
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
        ">
          <h3 style="margin: 0 0 16px 0; color: #ff6b35;">⚠️ Notification Permission</h3>
          <p style="margin: 0 0 20px 0; color: #666; line-height: 1.5;">
            Notification permission was not granted. You may not receive incoming call notifications when the app is in the background.
          </p>
          <p style="margin: 0 0 20px 0; color: #666; font-size: 14px;">
            You can enable notifications later in your device settings.
          </p>
          <button id="dismissWarning" style="
            background: #007AFF;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 16px;
            cursor: pointer;
            min-width: 100px;
          ">OK</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(warningDialog);
    
    // Handle dismiss
    const dismissButton = warningDialog.querySelector('#dismissWarning');
    dismissButton.addEventListener('click', () => {
      document.body.removeChild(warningDialog);
    });
    
    // Auto-dismiss after 10 seconds
    setTimeout(() => {
      if (document.body.contains(warningDialog)) {
        document.body.removeChild(warningDialog);
      }
    }, 10000);
    
    this.showStatus('Login successful - notification permission not granted', 'info');
  }

  async fetchAccessToken(identity) {
    const backendUrl = 'https://twilio-backend-82.localcan.dev';
    
    // Detect platform dynamically
    const platform = Capacitor.getPlatform(); // 'ios', 'android', or 'web'
    const platformEndpoint = platform === 'ios' ? 'ios' : 'android'; // default to android for web/unknown
    
    try {
      const response = await fetch(`${backendUrl}/accessToken/${platformEndpoint}?identity=${encodeURIComponent(identity)}`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch access token: ${response.status} ${response.statusText}`);
      }
      
      const accessToken = await response.text();
      return accessToken.trim();
    } catch (error) {
      console.error('Error fetching access token:', error);
      throw new Error(`Failed to get access token from server: ${error.message}`);
    }
  }

  async handleLogin() {
    const identity = this.identityInput.value.trim();
    if (!identity) {
      this.showStatus('Please enter an identity', 'error');
      return;
    }

    this.loginButton.disabled = true;
    this.loginButton.textContent = 'Fetching token...';

    try {
      // Fetch access token from backend
      this.showStatus('Fetching access token from server...', 'info');
      const accessToken = await this.fetchAccessToken(identity);
      
      // Login with the fetched token
      this.loginButton.textContent = 'Logging in...';
      const result = await CapacitorTwilioVoice.login({ accessToken });
      
      if (result.success) {
        // Check and request notification permissions after successful login
        await this.checkNotificationPermissions();
        
        this.isLoggedIn = true;
        this.currentIdentity = identity;
        this.showStatus(`Successfully logged in as ${identity}`, 'success');
        this.updateUserIdentityDisplay();
        this.showCallContainer();
        this.updateUI();
      } else {
        throw new Error('Login failed');
      }
    } catch (error) {
      console.error('Login error:', error);
      this.showStatus(`Login failed: ${error.message}`, 'error');
    } finally {
      this.loginButton.disabled = false;
      this.loginButton.textContent = 'Get Access Token & Login';
    }
  }

  async handleLogout() {
    this.logoutButton.disabled = true;
    this.logoutButton.textContent = 'Logging out...';

    try {
      // End any active calls first
      if (this.isCallActive) {
        await this.endCall();
      }

      // Call the logout method
      const result = await CapacitorTwilioVoice.logout();
      
      if (result.success) {
        // Reset all state
        this.isLoggedIn = false;
        this.currentIdentity = null;
        this.currentCallSid = null;
        this.isCallActive = false;
        this.isConnecting = false;
        this.isMuted = false;
        this.isSpeakerOn = false;
        this.updateUserIdentityDisplay();
        
        // Reset UI
        this.stopSpinning();
        this.resetCallButton();
        this.callControls.classList.remove('visible');
        this.callInfo.textContent = '';
        this.hideIncomingCallScreen();
        this.showLoginContainer();
        this.updateUI();
        
        this.showStatus('Successfully logged out', 'success');
      } else {
        throw new Error('Logout failed');
      }
    } catch (error) {
      console.error('Logout error:', error);
      this.showStatus(`Logout failed: ${error.message}`, 'error');
    } finally {
      this.logoutButton.disabled = false;
      this.logoutButton.textContent = 'Logout';
    }
  }

  async handleCallButtonClick() {
    if (this.isConnecting) return;

    try {
      if (this.isCallActive) {
        // End the call
        await this.endCall();
      } else {
        // Start a new call
        await this.makeCall();
      }
    } catch (error) {
      console.error('Call button error:', error);
      this.showStatus(`Call failed: ${error.message}`, 'error');
    }
  }

  async makeCall() {
    // Check microphone permission first
    const permissionResult = await CapacitorTwilioVoice.checkMicrophonePermission();
    if (!permissionResult.granted) {
      const requestResult = await CapacitorTwilioVoice.requestMicrophonePermission();
      if (!requestResult.granted) {
        this.showStatus('Microphone permission required', 'error');
        return;
      }
    }

    const phoneNumber = this.phoneInput.value.trim();
    const to = phoneNumber || ''; // Default to empty string. The backend will handle the call and the logic behind it.

    this.isConnecting = true;
    this.startSpinning();
    this.callButton.textContent = 'Connecting...';
    this.callButton.disabled = true;
    this.callInfo.textContent = `Calling ${to}...`;

    try {
      const result = await CapacitorTwilioVoice.makeCall({ to });
      if (result.success) {
        this.currentCallSid = result.callSid;
        console.log('Call initiated with SID:', this.currentCallSid);
      } else {
        throw new Error('Failed to initiate call');
      }
    } catch (error) {
      this.isConnecting = false;
      this.stopSpinning();
      this.resetCallButton();
      throw error;
    }
  }

  async endCall() {
    try {
      const result = await CapacitorTwilioVoice.endCall({ 
        callSid: this.currentCallSid 
      });
      if (result.success) {
        console.log('Call ended successfully');
      }
    } catch (error) {
      console.error('Error ending call:', error);
      this.showStatus('Failed to end call', 'error');
    }
  }

  async handleIncomingCall(data) {
    console.log(`Incoming call from ${data.from} (callSid: ${data.callSid})`);
    
    // Store the pending call invite
    this.pendingCallInvite = data;
    
    // Show the incoming call screen
    this.showIncomingCallScreen(data.from);
  }

  showIncomingCallScreen(callerName) {
    this.callerName.textContent = callerName || 'Unknown Caller';
    this.incomingCallScreen.classList.add('visible');
  }

  hideIncomingCallScreen() {
    this.incomingCallScreen.classList.remove('visible');
    this.pendingCallInvite = null;
  }

  async handleAcceptCall() {
    if (!this.pendingCallInvite) return;

    try {
      const result = await CapacitorTwilioVoice.acceptCall({ 
        callSid: this.pendingCallInvite.callSid 
      });
      
      if (result.success) {
        this.currentCallSid = this.pendingCallInvite.callSid;
        this.showStatus(`Accepting call from ${this.pendingCallInvite.from}`, 'success');
        this.hideIncomingCallScreen();
      } else {
        throw new Error('Failed to accept call');
      }
    } catch (error) {
      console.error('Error accepting call:', error);
      this.showStatus('Failed to accept call', 'error');
    }
  }

  async handleRejectCall() {
    if (!this.pendingCallInvite) return;

    try {
      const result = await CapacitorTwilioVoice.rejectCall({ 
        callSid: this.pendingCallInvite.callSid 
      });
      
      if (result.success) {
        this.showStatus('Call rejected', 'success');
        this.hideIncomingCallScreen();
      } else {
        throw new Error('Failed to reject call');
      }
    } catch (error) {
      console.error('Error rejecting call:', error);
      this.showStatus('Failed to reject call', 'error');
    }
  }

  handleCallConnected(data) {
    this.isCallActive = true;
    this.isConnecting = false;
    this.currentCallSid = data.callSid;
    
    // Hide incoming call screen if it's visible (in case call was accepted from notification)
    this.hideIncomingCallScreen();
    
    this.stopSpinning();
    this.callButton.textContent = 'Hang Up';
    this.callButton.classList.add('hang-up');
    this.callButton.disabled = false;
    this.callInfo.textContent = `Connected as ${this.currentIdentity}`;
    this.callControls.classList.add('visible');
    
    // Sync UI switches with JavaScript state
    this.muteSwitch.classList.toggle('on', this.isMuted);
    this.speakerSwitch.classList.toggle('on', this.isSpeakerOn);
    
    this.showStatus('Call connected', 'success');
  }

  handleCallDisconnected(data) {
    this.isCallActive = false;
    this.isConnecting = false;
    this.currentCallSid = null;
    
    // Reset call controls to defaults for next call
    this.isMuted = false;
    this.isSpeakerOn = false;
    
    this.stopSpinning();
    this.resetCallButton();
    this.callControls.classList.remove('visible');
    this.callInfo.textContent = '';
    
    // Reset control switches to default state
    this.muteSwitch.classList.remove('on');
    this.speakerSwitch.classList.remove('on');
    
    // Hide incoming call screen if it's visible
    this.hideIncomingCallScreen();
    
    if (data.error) {
      this.showStatus(`Call ended: ${data.error}`, 'error');
    } else if (data.rejectedFromNotification) {
      this.showStatus('Call rejected from notification', 'success');
    } else {
      this.showStatus('Call ended', 'success');
    }
  }

  async toggleMute() {
    try {
      this.isMuted = !this.isMuted;
      const result = await CapacitorTwilioVoice.muteCall({ 
        muted: this.isMuted,
        callSid: this.currentCallSid 
      });
      
      if (result.success) {
        this.muteSwitch.classList.toggle('on', this.isMuted);
        this.showStatus(this.isMuted ? 'Muted' : 'Unmuted', 'success');
      }
    } catch (error) {
      console.error('Error toggling mute:', error);
      this.showStatus('Failed to toggle mute', 'error');
    }
  }

  async toggleSpeaker() {
    try {
      this.isSpeakerOn = !this.isSpeakerOn;
      const result = await CapacitorTwilioVoice.setSpeaker({ 
        enabled: this.isSpeakerOn 
      });
      
      if (result.success) {
        this.speakerSwitch.classList.toggle('on', this.isSpeakerOn);
        this.showStatus(this.isSpeakerOn ? 'Speaker On' : 'Speaker Off', 'success');
      }
    } catch (error) {
      console.error('Error toggling speaker:', error);
      this.showStatus('Failed to toggle speaker', 'error');
    }
  }

  handleQualityWarnings(data) {
    const warnings = data.currentWarnings;
    if (warnings && warnings.length > 0) {
      const warningText = `Warnings: ${warnings.join(', ')}`;
      this.qualityWarnings.textContent = warningText;
      this.qualityWarnings.style.display = 'block';
      
      // Hide after 5 seconds
      setTimeout(() => {
        this.qualityWarnings.style.display = 'none';
      }, 5000);
    }
  }

  startSpinning() {
    // Animation removed
  }

  stopSpinning() {
    // Animation removed
  }

  resetCallButton() {
    this.callButton.textContent = 'Call';
    this.callButton.classList.remove('hang-up');
    this.callButton.disabled = false;
  }

  showStatus(message, type = 'success') {
    this.statusIndicator.textContent = message;
    this.statusIndicator.classList.remove('error', 'info');
    
    if (type === 'error') {
      this.statusIndicator.classList.add('error');
    } else if (type === 'info') {
      this.statusIndicator.classList.add('info');
    }
    
    this.statusIndicator.classList.add('visible');
    
    // Hide after different durations based on type
    const duration = type === 'info' ? 2000 : 3000;
    setTimeout(() => {
      this.statusIndicator.classList.remove('visible');
    }, duration);
  }

  updateUI() {
    // Show/hide login and call containers based on login state
    this.loginContainer.classList.toggle('hidden', this.isLoggedIn);
    this.callContainer.classList.toggle('visible', this.isLoggedIn);
    
    // Initialize call UI state
    if (this.isLoggedIn) {
      this.muteSwitch.classList.toggle('on', this.isMuted);
      this.speakerSwitch.classList.toggle('on', this.isSpeakerOn);
      this.callControls.classList.toggle('visible', this.isCallActive);
    }
  }

  async getCallStatus() {
    try {
      const status = await CapacitorTwilioVoice.getCallStatus();
      console.log('Call status:', status);
      return status;
    } catch (error) {
      console.error('Error getting call status:', error);
      return null;
    }
  }
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
  window.twilioApp = new TwilioVoiceApp();
});

// Global functions for debugging
window.login = (identity) => {
  if (identity) document.getElementById('identityInput').value = identity;
  return window.twilioApp?.handleLogin();
};
window.logout = () => window.twilioApp?.handleLogout();
window.fetchToken = (identity) => window.twilioApp?.fetchAccessToken(identity || 'alice');
window.getCallStatus = () => window.twilioApp?.getCallStatus();
window.checkLogin = () => window.twilioApp?.checkLoginStatus();
window.endCall = () => window.twilioApp?.endCall();
window.toggleMute = () => window.twilioApp?.toggleMute();
window.toggleSpeaker = () => window.twilioApp?.toggleSpeaker();
window.checkNotificationPermission = () => window.twilioApp?.checkNotificationPermissions();

// Debug incoming call UI
window.showIncomingCall = (callerName = 'Test Caller') => {
  window.twilioApp?.showIncomingCallScreen(callerName);
};
window.hideIncomingCall = () => window.twilioApp?.hideIncomingCallScreen();
