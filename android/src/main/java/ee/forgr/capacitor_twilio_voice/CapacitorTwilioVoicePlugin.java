package ee.forgr.capacitor_twilio_voice;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.UnregistrationListener;
import com.twilio.voice.Voice;

import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@CapacitorPlugin(
    name = "CapacitorTwilioVoice",
    permissions = {
        @Permission(strings = {Manifest.permission.RECORD_AUDIO}),
        @Permission(strings = {Manifest.permission.WAKE_LOCK}),
        @Permission(strings = {Manifest.permission.USE_FULL_SCREEN_INTENT})
    }
)
public class CapacitorTwilioVoicePlugin extends Plugin {
    private static final String TAG = "CapacitorTwilioVoice";
    private static final String PREF_ACCESS_TOKEN = "twilio_access_token";
    private static final String PREF_FCM_TOKEN = "twilio_fcm_token";
    
    public static CapacitorTwilioVoicePlugin instance;
    
    private String accessToken;
    private String fcmToken;
    private Map<String, CallInvite> activeCallInvites = new HashMap<>();
    private Map<String, Call> activeCalls = new HashMap<>();
    private Map<UUID, Call> callsByUuid = new HashMap<>();
    private Call activeCall;
    
    private AudioSwitch audioSwitch;
    
    // Notification and sound management
    private static final String NOTIFICATION_CHANNEL_ID = "twilio_voice_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "Twilio Voice Calls";
    private static final int INCOMING_CALL_NOTIFICATION_ID = 1001;
    private static final String ACTION_ACCEPT_CALL = "ACTION_ACCEPT_CALL";
    private static final String ACTION_REJECT_CALL = "ACTION_REJECT_CALL";
    private static final String EXTRA_CALL_SID = "EXTRA_CALL_SID";
    
    private MediaPlayer ringtonePlayer;
    private Vibrator vibrator;

    public static CapacitorTwilioVoicePlugin getInstance() {
        return instance;
    }

    @Override
    public void load() {
        super.load();
        
        // Set instance for Firebase messaging service
        instance = this;
        
        // Load stored access token
        SharedPreferences prefs = getContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
        accessToken = prefs.getString(PREF_ACCESS_TOKEN, null);
        
        // Initialize FCM and register for push notifications
        initializeFCM();
        
        // Initialize AudioSwitch
        initializeAudioSwitch();
        
        // Initialize notification system
        initializeNotifications();
        
        // Initialize sound and vibration
        initializeSoundAndVibration();
        
        Log.d(TAG, "CapacitorTwilioVoice plugin loaded");
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        
        // Clean up AudioSwitch
        if (audioSwitch != null) {
            audioSwitch.stop();
            audioSwitch = null;
        }
        
        // Clean up ringtone and notifications
        stopRingtone();
        dismissIncomingCallNotification();
        
        // Clear plugin instance
        instance = null;
        
        Log.d(TAG, "CapacitorTwilioVoice plugin destroyed");
    }

    private void initializeNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Incoming voice calls");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeSoundAndVibration() {
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void startRingtone() {
        try {
            if (ringtonePlayer != null) {
                stopRingtone();
            }
            
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtonePlayer = new MediaPlayer();
            ringtonePlayer.setDataSource(getContext(), ringtoneUri);
            ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
            ringtonePlayer.setLooping(true);
            ringtonePlayer.prepare();
            ringtonePlayer.start();
            
            // Start vibration pattern
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 1000, 1000}; // Wait 0ms, vibrate 1000ms, wait 1000ms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
            
            Log.d(TAG, "Started ringtone and vibration");
        } catch (Exception e) {
            Log.e(TAG, "Error starting ringtone: " + e.getMessage(), e);
        }
    }

    private void stopRingtone() {
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) {
                    ringtonePlayer.stop();
                }
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
            
            if (vibrator != null) {
                vibrator.cancel();
            }
            
            Log.d(TAG, "Stopped ringtone and vibration");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ringtone: " + e.getMessage(), e);
        }
    }

    private void initializeFCM() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    fcmToken = task.getResult();
                    Log.d(TAG, "FCM Registration Token: " + fcmToken);
                    
                    // Store FCM token
                    SharedPreferences prefs = getContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
                    prefs.edit().putString(PREF_FCM_TOKEN, fcmToken).apply();
                    
                    // Register with Twilio if we have an access token
                    if (accessToken != null && isTokenValid(accessToken)) {
                        performRegistration();
                    }
                }
            });
    }

    private boolean isTokenValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            
            // Use Android's Base64 (available since API 8) instead of Java's (API 26+)
            String payload = new String(Base64.decode(parts[1], Base64.DEFAULT));
            JSONObject json = new JSONObject(payload);
            
            long exp = json.getLong("exp");
            long currentTime = System.currentTimeMillis() / 1000;
            
            return currentTime < exp;
        } catch (Exception e) {
            Log.e(TAG, "Error validating token", e);
            return false;
        }
    }

    private void performRegistration() {
        if (accessToken == null || fcmToken == null) {
            Log.w(TAG, "Cannot register: missing access token or FCM token");
            return;
        }
        
        Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
    }

    private void initializeAudioSwitch() {
        audioSwitch = new AudioSwitch(getContext().getApplicationContext(), null, true);
        audioSwitch.start((audioDevices, selectedDevice) -> {
            Log.d(TAG, "Available audio devices: " + audioDevices.size());
            for (AudioDevice device : audioDevices) {
                Log.d(TAG, "Available device: " + device.getName());
            }
            if (selectedDevice != null) {
                Log.d(TAG, "Selected audio device: " + selectedDevice.getName());
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    @PluginMethod
    public void login(PluginCall call) {
        String token = call.getString("accessToken");
        if (token == null) {
            call.reject("accessToken is required");
            return;
        }
        
        if (!isTokenValid(token)) {
            call.reject("Invalid or expired access token");
            return;
        }
        
        // Store access token
        accessToken = token;
        SharedPreferences prefs = getContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_ACCESS_TOKEN, token).apply();
        
        Log.d(TAG, "Access token stored and validated successfully");
        
        // Perform registration
        performRegistration();
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void logout(PluginCall call) {
        Log.d(TAG, "Logging out and clearing stored credentials");
        
        // Unregister from Twilio
        if (accessToken != null && fcmToken != null) {
            Voice.unregister(accessToken, Voice.RegistrationChannel.FCM, fcmToken, unregistrationListener);
        }
        
        // Clear stored tokens
        SharedPreferences prefs = getContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_ACCESS_TOKEN).remove(PREF_FCM_TOKEN).apply();
        
        // Clear instance variables
        accessToken = null;
        
        // End any active calls
        for (Call call1 : activeCalls.values()) {
            call1.disconnect();
        }
        for (Call call1 : callsByUuid.values()) {
            call1.disconnect();
        }
        activeCalls.clear();
        callsByUuid.clear();
        activeCallInvites.clear();
        activeCall = null;
        
        // Deactivate AudioSwitch
        if (audioSwitch != null) {
            audioSwitch.deactivate();
        }
        
        Log.d(TAG, "Logout completed successfully");
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void isLoggedIn(PluginCall call) {
        boolean isLoggedIn = false;
        String identity = null;
        
        if (accessToken != null) {
            isLoggedIn = isTokenValid(accessToken);
            
            if (isLoggedIn) {
                identity = extractIdentityFromToken(accessToken);
            }
        }
        
        JSObject ret = new JSObject();
        ret.put("isLoggedIn", isLoggedIn);
        ret.put("hasValidToken", isLoggedIn);
        if (identity != null) {
            ret.put("identity", identity);
        }
        call.resolve(ret);
    }

    private String extractIdentityFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            
            // Use Android's Base64 (available since API 8) instead of Java's (API 26+)
            String payload = new String(Base64.decode(parts[1], Base64.DEFAULT));
            JSONObject json = new JSONObject(payload);
            JSONObject grants = json.getJSONObject("grants");
            
            return grants.optString("identity", null);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting identity from token", e);
            return null;
        }
    }

    @PluginMethod
    public void makeCall(PluginCall call) {
        if (accessToken == null) {
            call.reject("No access token available. Please call login() first.");
            return;
        }
        
        String to = call.getString("to");
        if (to == null) {
            to = ""; // Empty string for echo test
        }
        
        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
            .params(createCallParams(to))
            .build();
            
        Call voiceCall = Voice.connect(getContext(), connectOptions, callListener);
        
        if (voiceCall != null) {
            // Generate UUID for tracking (Android doesn't use UUID in ConnectOptions)
            UUID callUuid = UUID.randomUUID();
            callsByUuid.put(callUuid, voiceCall);
            activeCall = voiceCall;
            
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("callSid", callUuid.toString()); // Return UUID as callSid for now
            call.resolve(ret);
        } else {
            call.reject("Failed to connect call");
        }
    }

    private Map<String, String> createCallParams(String to) {
        Map<String, String> params = new HashMap<>();
        params.put("to", to);
        return params;
    }

    @PluginMethod
    public void acceptCall(PluginCall call) {
        String callSid = call.getString("callSid");
        if (callSid == null) {
            call.reject("callSid is required");
            return;
        }
        
        CallInvite callInvite = activeCallInvites.get(callSid);
        if (callInvite == null) {
            call.reject("No pending call invite found");
            return;
        }
        
        // Dismiss notification and stop sounds
        dismissIncomingCallNotification();
        
        Call acceptedCall = callInvite.accept(getContext(), callListener);
        if (acceptedCall != null) {
            // Store initially by the invite ID, will be updated when SID becomes available
            activeCall = acceptedCall;
            activeCallInvites.remove(callSid);
            
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } else {
            call.reject("Failed to accept call");
        }
    }

    @PluginMethod
    public void rejectCall(PluginCall call) {
        String callSid = call.getString("callSid");
        if (callSid == null) {
            call.reject("callSid is required");
            return;
        }
        
        CallInvite callInvite = activeCallInvites.get(callSid);
        if (callInvite == null) {
            call.reject("No pending call invite found");
            return;
        }
        
        // Dismiss notification and stop sounds
        dismissIncomingCallNotification();
        
        callInvite.reject(getContext());
        activeCallInvites.remove(callSid);
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void endCall(PluginCall call) {
        String callSid = call.getString("callSid");
        
        Call targetCall = null;
        if (callSid != null) {
            // Try to find by SID first
            targetCall = activeCalls.get(callSid);
            // If not found, try UUID format
            if (targetCall == null) {
                try {
                    UUID callUuid = UUID.fromString(callSid);
                    targetCall = callsByUuid.get(callUuid);
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID, ignore
                }
            }
        } else if (activeCall != null) {
            targetCall = activeCall;
        }
        
        if (targetCall != null) {
            targetCall.disconnect();
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } else {
            call.reject("No active call found");
        }
    }

    @PluginMethod
    public void muteCall(PluginCall call) {
        boolean muted = call.getBoolean("muted", false);
        String callSid = call.getString("callSid");
        
        Call targetCall = null;
        if (callSid != null) {
            // Try to find by SID first
            targetCall = activeCalls.get(callSid);
            // If not found, try UUID format
            if (targetCall == null) {
                try {
                    UUID callUuid = UUID.fromString(callSid);
                    targetCall = callsByUuid.get(callUuid);
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID, ignore
                }
            }
        } else if (activeCall != null) {
            targetCall = activeCall;
        }
        
        if (targetCall != null) {
            targetCall.mute(muted);
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } else {
            call.reject("No active call found");
        }
    }

    @PluginMethod
    public void setSpeaker(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        
        if (audioSwitch != null) {
            List<AudioDevice> availableDevices = audioSwitch.getAvailableAudioDevices();
            AudioDevice targetDevice = null;
            
            if (enabled) {
                // Find speaker device by name
                for (AudioDevice device : availableDevices) {
                    String deviceName = device.getName().toLowerCase();
                    if (deviceName.contains("speaker")) {
                        targetDevice = device;
                        break;
                    }
                }
            } else {
                // Find earpiece or wired headset (non-speaker) by name
                for (AudioDevice device : availableDevices) {
                    String deviceName = device.getName().toLowerCase();
                    if (deviceName.contains("earpiece") || 
                        deviceName.contains("wired") ||
                        deviceName.contains("headset")) {
                        targetDevice = device;
                        break;
                    }
                }
                
                // If no earpiece/headset found, use the first non-speaker device
                if (targetDevice == null) {
                    for (AudioDevice device : availableDevices) {
                        String deviceName = device.getName().toLowerCase();
                        if (!deviceName.contains("speaker")) {
                            targetDevice = device;
                            break;
                        }
                    }
                }
            }
            
            if (targetDevice != null) {
                audioSwitch.selectDevice(targetDevice);
                Log.d(TAG, "Audio device switched to: " + targetDevice.getName());
                
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } else {
                Log.w(TAG, "Target audio device not available");
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("error", "Target audio device not available");
                call.resolve(ret);
            }
        } else {
            call.reject("AudioSwitch not initialized");
        }
    }

    @PluginMethod
    public void getCallStatus(PluginCall call) {
        JSObject ret = new JSObject();
        
        if (activeCall != null) {
            ret.put("hasActiveCall", true);
            String callSid = activeCall.getSid();
            ret.put("callSid", callSid != null ? callSid : "pending");
            ret.put("isMuted", activeCall.isMuted());
            ret.put("isOnHold", activeCall.isOnHold());
        } else {
            ret.put("hasActiveCall", false);
        }
        
        ret.put("pendingInvites", activeCallInvites.size());
        ret.put("activeCallsCount", activeCalls.size() + callsByUuid.size());
        call.resolve(ret);
    }

    @PluginMethod
    public void checkMicrophonePermission(PluginCall call) {
        boolean hasPermission = ActivityCompat.checkSelfPermission(getContext(), 
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        JSObject ret = new JSObject();
        ret.put("granted", hasPermission);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestMicrophonePermission(PluginCall call) {
        requestPermissions(call);
    }

    // Twilio Voice Listeners
    private final RegistrationListener registrationListener = new RegistrationListener() {
        @Override
        public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
            Log.d(TAG, "Successfully registered for VoIP push notifications");
            
            JSObject data = new JSObject();
            data.put("fcmToken", fcmToken);
            notifyListeners("registrationSuccess", data);
        }

        @Override
        public void onError(@NonNull RegistrationException registrationException, 
                          @NonNull String accessToken, @NonNull String fcmToken) {
            Log.e(TAG, "Registration error: " + registrationException.getMessage());
            
            JSObject data = new JSObject();
            data.put("error", registrationException.getMessage());
            data.put("code", registrationException.getErrorCode());
            notifyListeners("registrationFailure", data);
        }
    };

    private final UnregistrationListener unregistrationListener = new UnregistrationListener() {
        @Override
        public void onUnregistered(@NonNull String accessToken, @NonNull String fcmToken) {
            Log.d(TAG, "Successfully unregistered from VoIP push notifications");
        }

        @Override
        public void onError(@NonNull RegistrationException registrationException, 
                          @NonNull String accessToken, @NonNull String fcmToken) {
            Log.e(TAG, "Unregistration error: " + registrationException.getMessage());
        }
    };

    private final Call.Listener callListener = new Call.Listener() {
        @Override
        public void onRinging(@NonNull Call call) {
            Log.d(TAG, "Call is ringing");
            
            // Now we have the actual SID, update our mapping
            String callSid = call.getSid();
            if (callSid != null) {
                activeCalls.put(callSid, call);
                
                // Find and remove from UUID mapping
                UUID callUuid = null;
                for (Map.Entry<UUID, Call> entry : callsByUuid.entrySet()) {
                    if (entry.getValue() == call) {
                        callUuid = entry.getKey();
                        break;
                    }
                }
                if (callUuid != null) {
                    callsByUuid.remove(callUuid);
                }
            }
            
            JSObject data = new JSObject();
            data.put("callSid", callSid != null ? callSid : "unknown");
            notifyListeners("callRinging", data);
        }

        @Override
        public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
            Log.e(TAG, "Call connect failure: " + callException.getMessage());
            
            String callSid = call.getSid();
            if (callSid != null) {
                activeCalls.remove(callSid);
            }
            
            // Also remove from UUID mapping
            UUID callUuid = null;
            for (Map.Entry<UUID, Call> entry : callsByUuid.entrySet()) {
                if (entry.getValue() == call) {
                    callUuid = entry.getKey();
                    break;
                }
            }
            if (callUuid != null) {
                callsByUuid.remove(callUuid);
            }
            
            if (activeCall == call) {
                activeCall = null;
            }
            
            // Deactivate AudioSwitch when call fails
            if (audioSwitch != null && activeCalls.isEmpty() && callsByUuid.isEmpty()) {
                audioSwitch.deactivate();
            }
            
            JSObject data = new JSObject();
            data.put("callSid", callSid != null ? callSid : "unknown");
            data.put("error", callException.getMessage());
            data.put("code", callException.getErrorCode());
            notifyListeners("callDisconnected", data);
        }

        @Override
        public void onConnected(@NonNull Call call) {
            Log.d(TAG, "Call connected");
            
            // Activate AudioSwitch for call
            if (audioSwitch != null) {
                audioSwitch.activate();
            }
            
            // Ensure we have the SID mapping (should already be done in onRinging, but just in case)
            String callSid = call.getSid();
            if (callSid != null && !activeCalls.containsKey(callSid)) {
                activeCalls.put(callSid, call);
                
                // Find and remove from UUID mapping if it exists
                UUID callUuid = null;
                for (Map.Entry<UUID, Call> entry : callsByUuid.entrySet()) {
                    if (entry.getValue() == call) {
                        callUuid = entry.getKey();
                        break;
                    }
                }
                if (callUuid != null) {
                    callsByUuid.remove(callUuid);
                }
            }
            
            JSObject data = new JSObject();
            data.put("callSid", callSid != null ? callSid : "unknown");
            data.put("from", call.getFrom());
            data.put("to", call.getTo());
            notifyListeners("callConnected", data);
        }

        @Override
        public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
            Log.d(TAG, "Call reconnecting");
            
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            data.put("error", callException.getMessage());
            notifyListeners("callReconnecting", data);
        }

        @Override
        public void onReconnected(@NonNull Call call) {
            Log.d(TAG, "Call reconnected");
            
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            notifyListeners("callReconnected", data);
        }

        @Override
        public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
            Log.d(TAG, "Call disconnected");
            
            String callSid = call.getSid();
            if (callSid != null) {
                activeCalls.remove(callSid);
            }
            
            // Also remove from UUID mapping
            UUID callUuid = null;
            for (Map.Entry<UUID, Call> entry : callsByUuid.entrySet()) {
                if (entry.getValue() == call) {
                    callUuid = entry.getKey();
                    break;
                }
            }
            if (callUuid != null) {
                callsByUuid.remove(callUuid);
            }
            
            if (activeCall == call) {
                activeCall = null;
            }
            
            // Deactivate AudioSwitch when call ends
            if (audioSwitch != null && activeCalls.isEmpty() && callsByUuid.isEmpty()) {
                audioSwitch.deactivate();
            }
            
            JSObject data = new JSObject();
            data.put("callSid", callSid != null ? callSid : "unknown");
            if (callException != null) {
                data.put("error", callException.getMessage());
                data.put("code", callException.getErrorCode());
            }
            notifyListeners("callDisconnected", data);
        }

        @Override
        public void onCallQualityWarningsChanged(@NonNull Call call,
                                                @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                                @NonNull Set<Call.CallQualityWarning> previousWarnings) {
            Log.d(TAG, "Call quality warnings changed");
            
            JSArray currentWarningsArray = new JSArray();
            for (Call.CallQualityWarning warning : currentWarnings) {
                currentWarningsArray.put(warning.name().toLowerCase().replace('_', '-'));
            }
            
            JSArray previousWarningsArray = new JSArray();
            for (Call.CallQualityWarning warning : previousWarnings) {
                previousWarningsArray.put(warning.name().toLowerCase().replace('_', '-'));
            }
            
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            data.put("currentWarnings", currentWarningsArray);
            data.put("previousWarnings", previousWarningsArray);
            notifyListeners("callQualityWarningsChanged", data);
        }
    };

    private void showIncomingCallNotification(CallInvite callInvite, String callSid) {
        try {
            String callerName = callInvite.getFrom();
            if (callerName != null && callerName.startsWith("client:")) {
                callerName = callerName.substring(7); // Remove "client:" prefix
            }

            // Create intent for accepting the call
            Intent acceptIntent = new Intent(getContext(), NotificationActionReceiver.class);
            acceptIntent.setAction(ACTION_ACCEPT_CALL);
            acceptIntent.putExtra(EXTRA_CALL_SID, callSid);
            PendingIntent acceptPendingIntent = PendingIntent.getBroadcast(
                getContext(), 
                0, 
                acceptIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Create intent for rejecting the call
            Intent rejectIntent = new Intent(getContext(), NotificationActionReceiver.class);
            rejectIntent.setAction(ACTION_REJECT_CALL);
            rejectIntent.putExtra(EXTRA_CALL_SID, callSid);
            PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(
                getContext(), 
                1, 
                rejectIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Create intent for full screen
            Intent fullScreenIntent = new Intent(getContext(), getActivity().getClass());
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            fullScreenIntent.putExtra("INCOMING_CALL", true);
            fullScreenIntent.putExtra(EXTRA_CALL_SID, callSid);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                getContext(), 
                2, 
                fullScreenIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Incoming Call")
                .setContentText(callerName + " is calling")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPendingIntent)
                .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
                .setContentIntent(fullScreenPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
            notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build());
            
            Log.d(TAG, "Incoming call notification shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage(), e);
        }
    }

    private void dismissIncomingCallNotification() {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
            notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
            stopRingtone();
            Log.d(TAG, "Incoming call notification dismissed");
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing notification: " + e.getMessage(), e);
        }
    }

    // Handle incoming call invites (called from FirebaseMessagingService)
    public void handleCallInvite(CallInvite callInvite) {
        Log.d(TAG, "Received incoming call from: " + callInvite.getFrom());
        
        String callSid = UUID.randomUUID().toString(); // Generate a unique ID
        activeCallInvites.put(callSid, callInvite);
        
        // Create and show notification
        showIncomingCallNotification(callInvite, callSid);
        
        // Start ringtone and vibration
        startRingtone();
        
        JSObject data = new JSObject();
        data.put("callSid", callSid);
        data.put("from", callInvite.getFrom());
        data.put("to", callInvite.getTo());
        notifyListeners("callInviteReceived", data);
    }

    // Handle cancelled call invites
    public void handleCancelledCallInvite(CancelledCallInvite cancelledCallInvite) {
        Log.d(TAG, "Call invite cancelled");
        
        // Dismiss notification and stop sounds
        dismissIncomingCallNotification();
        
        // Find and remove the corresponding call invite
        String cancelledCallSid = null;
        for (Map.Entry<String, CallInvite> entry : activeCallInvites.entrySet()) {
            CallInvite invite = entry.getValue();
            if (invite.getCallSid().equals(cancelledCallInvite.getCallSid())) {
                cancelledCallSid = entry.getKey();
                break;
            }
        }
        
        if (cancelledCallSid != null) {
            activeCallInvites.remove(cancelledCallSid);
            
            JSObject data = new JSObject();
            data.put("callSid", cancelledCallSid);
            notifyListeners("callInviteCancelled", data);
        }
    }

    // Methods called by NotificationActionReceiver
    public void acceptCallFromNotification(String callSid) {
        Log.d(TAG, "Accepting call from notification: " + callSid);
        
        CallInvite callInvite = activeCallInvites.get(callSid);
        if (callInvite != null) {
            // Dismiss notification and stop sounds
            dismissIncomingCallNotification();
            
            // Accept the call - this will trigger the normal call flow
            Call acceptedCall = callInvite.accept(getContext(), callListener);
            if (acceptedCall != null) {
                activeCall = acceptedCall;
                activeCallInvites.remove(callSid);
                Log.d(TAG, "Call accepted from notification, waiting for onConnected callback");
            } else {
                Log.e(TAG, "Failed to accept call from notification");
            }
        } else {
            Log.e(TAG, "Call invite not found for SID: " + callSid);
        }
    }

    public void rejectCallFromNotification(String callSid) {
        Log.d(TAG, "Rejecting call from notification: " + callSid);
        
        CallInvite callInvite = activeCallInvites.get(callSid);
        if (callInvite != null) {
            // Dismiss notification and stop sounds
            dismissIncomingCallNotification();
            
            callInvite.reject(getContext());
            activeCallInvites.remove(callSid);
            
            Log.d(TAG, "Call rejected from notification");
            // Note: No need to emit callDisconnected since the call was never connected
        } else {
            Log.e(TAG, "Call invite not found for SID: " + callSid);
        }
    }
}
