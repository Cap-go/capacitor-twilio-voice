package ee.forgr.capacitor_twilio_voice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
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
import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.UnregistrationListener;
import com.twilio.voice.Voice;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "CapacitorTwilioVoice",
    permissions = {
        @Permission(strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(strings = { Manifest.permission.WAKE_LOCK }),
        @Permission(strings = { Manifest.permission.USE_FULL_SCREEN_INTENT })
    }
)
public class CapacitorTwilioVoicePlugin extends Plugin {

    private final String pluginVersion = "8.0.21";

    private static final String TAG = "CapacitorTwilioVoice";
    private static final String PREF_ACCESS_TOKEN = "twilio_access_token";
    private static final String PREF_FCM_TOKEN = "twilio_fcm_token";
    private static final String PREFS_NAME = "capacitor_twilio_voice_prefs";
    private static final String PREF_MIC_PERMISSION_REQUESTED = "mic_permission_requested";

    public static CapacitorTwilioVoicePlugin instance;

    private String accessToken;
    private String fcmToken;
    private Map<String, CallInvite> activeCallInvites = new HashMap<>();
    private Map<String, Call> activeCalls = new HashMap<>();
    private Map<UUID, Call> callsByUuid = new HashMap<>();
    private Call activeCall;

    private AudioSwitch audioSwitch;

    private Context injectedContext;

    private Class<?> mainActivityClass;

    // Notification and sound management
    private static final String NOTIFICATION_CHANNEL_ID = "twilio_voice_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "Twilio Voice Calls";
    private static final int INCOMING_CALL_NOTIFICATION_ID = 1001;
    private static final String ACTION_ACCEPT_CALL = "ACTION_ACCEPT_CALL";
    private static final String ACTION_REJECT_CALL = "ACTION_REJECT_CALL";
    private static final String EXTRA_CALL_SID = "EXTRA_CALL_SID";

    private MediaPlayer ringtonePlayer;
    private Vibrator vibrator;

    // Permission handling
    private static final int REQUEST_CODE_RECORD_AUDIO_FOR_ACCEPT = 2001;
    private String pendingCallSidForPermission;

    private enum PendingPermissionAction {
        NONE,
        OUTGOING_CALL,
        ACCEPT_CALL
    }

    private PendingPermissionAction pendingPermissionAction = PendingPermissionAction.NONE;
    private PluginCall pendingOutgoingCall;
    private String pendingOutgoingTo;
    private PluginCall pendingPermissionCall;
    private long permissionRequestTimestamp = 0L;
    private int permissionAttemptCount = 0;
    private boolean awaitingSettingsResult = false;
    private ActivityResultLauncher<String[]> micPermissionLauncher;

    // Voice Call Service
    private VoiceCallService voiceCallService;
    private boolean isServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "VoiceCallService connected");
            VoiceCallService.VoiceCallBinder binder = (VoiceCallService.VoiceCallBinder) service;
            voiceCallService = binder.getService();
            isServiceBound = true;

            // Set up service listener to relay events to JavaScript
            voiceCallService.setServiceListener(serviceListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "VoiceCallService disconnected");
            voiceCallService = null;
            isServiceBound = false;
        }
    };

    // Service listener to relay events from the service to JavaScript
    private final VoiceCallService.VoiceCallServiceListener serviceListener = new VoiceCallService.VoiceCallServiceListener() {
        @Override
        public void onCallConnected(Call call) {
            activeCall = call;
            activeCalls.put(call.getSid(), call);

            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            notifyListeners("callConnected", data);
        }

        @Override
        public void onCallDisconnected(Call call, CallException error) {
            activeCall = null;
            activeCalls.remove(call.getSid());

            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            if (error != null) {
                data.put("error", error.getMessage());
            }
            notifyListeners("callDisconnected", data);
            moveAppToBackgroundIfLocked();
        }

        @Override
        public void onCallRinging(Call call) {
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            notifyListeners("callRinging", data);
        }

        @Override
        public void onCallReconnecting(Call call, CallException error) {
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            if (error != null) {
                data.put("error", error.getMessage());
            }
            notifyListeners("callReconnecting", data);
        }

        @Override
        public void onCallReconnected(Call call) {
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());
            notifyListeners("callReconnected", data);
        }

        @Override
        public void onCallQualityWarningsChanged(
            Call call,
            Set<Call.CallQualityWarning> currentWarnings,
            Set<Call.CallQualityWarning> previousWarnings
        ) {
            JSObject data = new JSObject();
            data.put("callSid", call.getSid());

            // Convert warnings to string array
            JSArray currentWarningsArray = new JSArray();
            for (Call.CallQualityWarning warning : currentWarnings) {
                currentWarningsArray.put(warning.name());
            }
            data.put("currentWarnings", currentWarningsArray);

            notifyListeners("callQualityWarningsChanged", data);
        }

        @Override
        public void onCallInviteAccepted(CallInvite callInvite) {
            // Remove from active invites since it's now being handled by the service
            activeCallInvites.remove(callInvite.getCallSid());
            dismissIncomingCallNotification();
        }
    };

    public static CapacitorTwilioVoicePlugin getInstance() {
        return instance;
    }

    @Override
    public void load() {
        super.load();

        // Set instance for Firebase messaging service
        instance = this;

        // Load stored access token
        SharedPreferences prefs = getSafeContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
        accessToken = prefs.getString(PREF_ACCESS_TOKEN, null);

        // Initialize FCM and register for push notifications
        initializeFCM();

        // Initialize AudioSwitch
        initializeAudioSwitch();

        // Initialize notification system
        initializeNotifications();

        // Initialize sound and vibration
        initializeSoundAndVibration();

        // Check if app was launched to auto-accept a call
        checkForAutoAcceptCall();

        // Check if app was launched due to an incoming call notification
        checkForIncomingCallNotification();

        // Bind to the VoiceCallService
        bindToVoiceCallService();

        Log.d(TAG, "CapacitorTwilioVoice plugin loaded");

        micPermissionLauncher = getBridge().registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (permissions) -> handleMicPermissionResult(permissions)
        );
    }

    private void bindToVoiceCallService() {
        Intent intent = new Intent(getSafeContext(), VoiceCallService.class);
        getSafeContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Binding to VoiceCallService");
    }

    // Service cleanup is handled when the activity is destroyed

    private void checkForAutoAcceptCall() {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                Intent intent = activity.getIntent();
                if (intent != null) {
                    // Check for auto-accept flag OR accept action
                    boolean shouldAutoAccept =
                        intent.getBooleanExtra("AUTO_ACCEPT_CALL", false) || ACTION_ACCEPT_CALL.equals(intent.getAction());

                    if (shouldAutoAccept) {
                        String callSid = intent.getStringExtra(EXTRA_CALL_SID);
                        Log.d(TAG, "App launched with auto-accept for call: " + callSid + " (action: " + intent.getAction() + ")");

                        if (callSid != null) {
                            // Clear the intent extras and action to prevent repeated auto-accept
                            intent.removeExtra("AUTO_ACCEPT_CALL");
                            intent.removeExtra(EXTRA_CALL_SID);
                            intent.setAction(null);

                            // Delay the auto-accept slightly to ensure plugin is fully loaded
                            new android.os.Handler().postDelayed(
                                () -> {
                                    Log.d(TAG, "Auto-accepting call: " + callSid);
                                    ensureMicPermissionThenAccept(callSid);
                                },
                                500
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for auto-accept call", e);
        }
    }

    private void checkForIncomingCallNotification() {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                Intent intent = activity.getIntent();
                if (intent != null && intent.getBooleanExtra("INCOMING_CALL", false)) {
                    String callSid = intent.getStringExtra(EXTRA_CALL_SID);
                    String callerName = intent.getStringExtra("CALLER_NAME");
                    String callFrom = intent.getStringExtra("CALL_FROM");

                    Log.d(TAG, "App opened via incoming call notification: " + callSid + " from: " + callFrom);

                    if (callSid != null && callFrom != null) {
                        // Clear the intent extras to prevent repeated notifications
                        intent.removeExtra("INCOMING_CALL");
                        intent.removeExtra(EXTRA_CALL_SID);
                        intent.removeExtra("CALLER_NAME");
                        intent.removeExtra("CALL_FROM");

                        // Check if we still have the call invite
                        CallInvite callInvite = activeCallInvites.get(callSid);
                        if (callInvite != null) {
                            // Delay sending the event to ensure JavaScript is ready
                            new android.os.Handler().postDelayed(
                                () -> {
                                    Log.d(TAG, "Sending incoming call event to JavaScript: " + callSid);

                                    // Strip "client:" prefix from caller name for consistency
                                    String fromValue = callFrom;
                                    if (fromValue != null && fromValue.startsWith("client:")) {
                                        fromValue = fromValue.substring(7); // Remove "client:" prefix
                                    }
                                    String callerNameValue = callerName != null ? callerName : callFrom;
                                    if (callerNameValue != null && callerNameValue.startsWith("client:")) {
                                        callerNameValue = callerNameValue.substring(7); // Remove "client:" prefix
                                    }

                                    JSObject data = new JSObject();
                                    data.put("callSid", callSid);
                                    data.put("from", fromValue);
                                    data.put("to", callInvite.getTo());
                                    data.put("callerName", callerNameValue);
                                    data.put("openedFromNotification", true);

                                    notifyListeners("callInviteReceived", data);
                                },
                                1000
                            ); // Give JavaScript more time to initialize
                        } else {
                            Log.w(TAG, "Call invite not found for SID: " + callSid + " (may have been cancelled)");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for incoming call notification", e);
        }
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

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        Log.d(
            TAG,
            "handleOnResume: hasPermission=" +
                hasMicrophonePermission() +
                ", awaitingSettings=" +
                awaitingSettingsResult +
                ", pendingCall=" +
                (pendingPermissionCall != null) +
                ", pendingAction=" +
                pendingPermissionAction
        );
        if (hasMicrophonePermission()) {
            if (pendingPermissionAction != PendingPermissionAction.NONE || pendingPermissionCall != null) {
                awaitingSettingsResult = false;
                Log.d(TAG, "handleOnResume: permission granted, resuming pending flow");
                handleMicrophonePermissionGranted();
            }
        } else if (awaitingSettingsResult && pendingPermissionCall != null) {
            awaitingSettingsResult = false;
            Log.d(TAG, "handleOnResume: permission still denied after returning from settings");
            JSObject ret = new JSObject();
            ret.put("granted", false);
            pendingPermissionCall.setKeepAlive(false);
            pendingPermissionCall.resolve(ret);
            pendingPermissionCall = null;
        } else if (awaitingSettingsResult && pendingPermissionAction == PendingPermissionAction.ACCEPT_CALL) {
            awaitingSettingsResult = false;
            Log.d(TAG, "handleOnResume: settings return without permission for accept flow");
            handlePermissionFailure();
        } else if (pendingPermissionCall != null) {
            Log.d(TAG, "handleOnResume: permission denied from dialog, invoking fallback handling");
            handleMicrophonePermissionDenied();
        }
    }

    public void setInjectedContext(Context injectedContext) {
        this.injectedContext = injectedContext;
    }

    public void setMainActivityClass(Class<?> mainActivityClass) {
        this.mainActivityClass = mainActivityClass;
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

            NotificationManager notificationManager = getSafeContext().getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeSoundAndVibration() {
        vibrator = (Vibrator) getSafeContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void ensureMicPermissionThenAccept(String callSid) {
        Log.d(TAG, "ensureMicPermissionThenAccept: callSid=" + callSid);
        if (hasMicrophonePermission()) {
            Log.d(TAG, "ensureMicPermissionThenAccept: permission granted, proceeding");
            proceedAcceptCall(callSid);
            return;
        }

        pendingCallSidForPermission = callSid;
        pendingPermissionAction = PendingPermissionAction.ACCEPT_CALL;
        permissionAttemptCount = 0;
        awaitingSettingsResult = false;
        Log.d(TAG, "ensureMicPermissionThenAccept: requesting permission before accepting call");
        requestMicrophonePermission();
    }

    // Helper to actually start the service once permission is granted
    private void proceedAcceptCall(String callSid) {
        CallInvite callInvite = activeCallInvites.get(callSid);
        if (callInvite == null) {
            Log.e(TAG, "No pending call invite for: " + callSid);
            return;
        }

        Intent serviceIntent = new Intent(getSafeContext(), VoiceCallService.class);
        serviceIntent.setAction(VoiceCallService.ACTION_ACCEPT_CALL);
        serviceIntent.putExtra(VoiceCallService.EXTRA_CALL_INVITE, callInvite);
        serviceIntent.putExtra(VoiceCallService.EXTRA_ACCESS_TOKEN, accessToken);

        try {
            getSafeContext().startForegroundService(serviceIntent);
            Log.d(TAG, "Call acceptance started via service (permission granted)");
        } catch (Exception e) {
            Log.e(TAG, "Error accepting call via service", e);
        } finally {
            pendingCallSidForPermission = null;
            if (pendingPermissionAction == PendingPermissionAction.ACCEPT_CALL) {
                pendingPermissionAction = PendingPermissionAction.NONE;
            }
            permissionAttemptCount = 0;
        }
    }

    private Context getSafeContext() {
        if (this.bridge != null) {
            return this.getContext();
        } else if (this.injectedContext != null) {
            return this.injectedContext;
        } else {
            throw new RuntimeException("Cannot find context");
        }
    }

    private void startRingtone() {
        try {
            if (ringtonePlayer != null) {
                stopRingtone();
            }

            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtonePlayer = new MediaPlayer();
            ringtonePlayer.setDataSource(getSafeContext(), ringtoneUri);
            ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
            ringtonePlayer.setLooping(true);
            ringtonePlayer.prepare();
            ringtonePlayer.start();

            // Start vibration pattern
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = { 0, 1000, 1000 }; // Wait 0ms, vibrate 1000ms, wait 1000ms
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
        FirebaseMessaging.getInstance()
            .getToken()
            .addOnCompleteListener(
                new OnCompleteListener<String>() {
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
                        SharedPreferences prefs = getSafeContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
                        prefs.edit().putString(PREF_FCM_TOKEN, fcmToken).apply();

                        // Register with Twilio if we have an access token
                        if (accessToken != null && isTokenValid(accessToken)) {
                            performRegistration();
                        }
                    }
                }
            );
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
        audioSwitch = new AudioSwitch(getSafeContext().getApplicationContext(), null, true);
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
        SharedPreferences prefs = getSafeContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
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
        SharedPreferences prefs = getSafeContext().getSharedPreferences("CapacitorTwilioVoice", Context.MODE_PRIVATE);
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

        if (pendingOutgoingCall != null) {
            pendingOutgoingCall.setKeepAlive(false);
            pendingOutgoingCall.reject("Another call is awaiting microphone permission.");
            clearOutgoingPermissionState();
        }

        String to = call.getString("to");
        if (to == null) {
            to = ""; // Empty string for echo test
        }

        if (hasMicrophonePermission()) {
            startOutgoingCall(call, to);
            return;
        }

        pendingOutgoingCall = call;
        pendingOutgoingTo = to;
        pendingPermissionAction = PendingPermissionAction.OUTGOING_CALL;
        permissionAttemptCount = 0;
        call.setKeepAlive(true);
        requestMicrophonePermission();
    }

    private void startOutgoingCall(PluginCall call, String to) {
        Log.d(TAG, "startOutgoingCall: to=" + to);
        // Start call via the foreground service
        Intent serviceIntent = new Intent(getSafeContext(), VoiceCallService.class);
        serviceIntent.setAction(VoiceCallService.ACTION_START_CALL);
        serviceIntent.putExtra(VoiceCallService.EXTRA_CALL_TO, to);
        serviceIntent.putExtra(VoiceCallService.EXTRA_ACCESS_TOKEN, accessToken);

        try {
            getSafeContext().startForegroundService(serviceIntent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("callSid", "pending"); // Will be updated when service connects
            call.setKeepAlive(false);
            call.resolve(ret);
        } catch (Exception e) {
            call.setKeepAlive(false);
            Log.e(TAG, "Error starting call service", e);
            call.reject("Failed to start call: " + e.getMessage());
        } finally {
            clearOutgoingPermissionState();
        }
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(getSafeContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private SharedPreferences getPrefs() {
        return getSafeContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void requestMicrophonePermission() {
        Activity activity = getActivity();
        permissionRequestTimestamp = System.currentTimeMillis();
        permissionAttemptCount++;

        if (activity != null) {
            activity.runOnUiThread(() -> {
                Log.d(TAG, "requestMicrophonePermission: requesting RECORD_AUDIO (attempt " + permissionAttemptCount + ")");
                if (micPermissionLauncher != null) {
                    micPermissionLauncher.launch(new String[] { Manifest.permission.RECORD_AUDIO });
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        new String[] { Manifest.permission.RECORD_AUDIO },
                        REQUEST_CODE_RECORD_AUDIO_FOR_ACCEPT
                    );
                }
            });
            return;
        }

        if (mainActivityClass != null) {
            Context context = getSafeContext();
            Intent launchIntent = new Intent(context, mainActivityClass);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (pendingPermissionAction == PendingPermissionAction.ACCEPT_CALL && pendingCallSidForPermission != null) {
                launchIntent.putExtra("AUTO_ACCEPT_CALL", true);
                launchIntent.putExtra(EXTRA_CALL_SID, pendingCallSidForPermission);
            }
            Log.d(TAG, "requestMicrophonePermission: launching activity to request permission");
            context.startActivity(launchIntent);
        } else {
            Log.w(TAG, "Unable to request microphone permission - no activity available");
            handlePermissionFailure();
        }
    }

    private void handleMicPermissionResult(Map<String, Boolean> permissions) {
        Boolean granted = permissions.get(Manifest.permission.RECORD_AUDIO);
        Log.d(TAG, "handleMicPermissionResult: granted=" + granted + ", pendingAction=" + pendingPermissionAction);
        if (granted != null && granted) {
            handleMicrophonePermissionGranted();
        } else {
            handleMicrophonePermissionDenied();
        }
    }

    private void handleMicrophonePermissionGranted() {
        Log.d(
            TAG,
            "handleMicrophonePermissionGranted: pendingAction=" +
                pendingPermissionAction +
                ", pendingCall=" +
                (pendingPermissionCall != null)
        );
        permissionAttemptCount = 0;

        if (pendingPermissionAction == PendingPermissionAction.OUTGOING_CALL && pendingOutgoingCall != null) {
            PluginCall call = pendingOutgoingCall;
            String to = pendingOutgoingTo != null ? pendingOutgoingTo : "";
            pendingOutgoingCall = null;
            pendingOutgoingTo = null;
            pendingPermissionAction = PendingPermissionAction.NONE;
            startOutgoingCall(call, to);
            return;
        }

        if (pendingPermissionAction == PendingPermissionAction.ACCEPT_CALL) {
            if (pendingCallSidForPermission != null) {
                String callSid = pendingCallSidForPermission;
                pendingCallSidForPermission = null;
                pendingPermissionAction = PendingPermissionAction.NONE;
                proceedAcceptCall(callSid);
                return;
            }

            pendingPermissionAction = PendingPermissionAction.NONE;
            awaitingSettingsResult = false;
            permissionAttemptCount = 0;
            return;
        }

        if (pendingPermissionCall != null) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            pendingPermissionCall.setKeepAlive(false);
            pendingPermissionCall.resolve(ret);
            pendingPermissionCall = null;
            awaitingSettingsResult = false;
        }

        pendingPermissionAction = PendingPermissionAction.NONE;
    }

    private void handleMicrophonePermissionDenied() {
        Log.d(TAG, "handleMicrophonePermissionDenied invoked");
        Activity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "handleMicrophonePermissionDenied: no activity");
            handlePermissionFailure();
            return;
        }

        boolean canRequestAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO);
        Log.d(
            TAG,
            "handleMicrophonePermissionDenied: canRequestAgain=" +
                canRequestAgain +
                ", attempt=" +
                permissionAttemptCount +
                ", pendingAction=" +
                pendingPermissionAction +
                ", standaloneCall=" +
                (pendingPermissionCall != null)
        );

        if (pendingPermissionAction == PendingPermissionAction.NONE && pendingPermissionCall != null) {
            if (canRequestAgain && permissionAttemptCount <= 1) {
                showStandalonePermissionRationaleDialog(activity, pendingPermissionCall);
            } else {
                showStandalonePermissionSettingsDialog(activity, pendingPermissionCall);
            }
        } else if (canRequestAgain && permissionAttemptCount <= 1) {
            showPermissionRationaleDialog(activity);
        } else {
            showPermissionSettingsDialog(activity);
        }
    }

    private void showPermissionRationaleDialog(Activity activity) {
        Log.d(TAG, "showPermissionRationaleDialog");
        new AlertDialog.Builder(activity)
            .setTitle("Microphone required")
            .setMessage("Microphone access is required to place and receive calls.")
            .setPositiveButton("Retry", (dialog, which) -> {
                dialog.dismiss();
                requestMicrophonePermission();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
                handlePermissionFailure();
            })
            .setCancelable(false)
            .show();
    }

    private void showStandalonePermissionRationaleDialog(Activity activity, PluginCall call) {
        Log.d(TAG, "showStandalonePermissionRationaleDialog");
        new AlertDialog.Builder(activity)
            .setTitle("Microphone required")
            .setMessage("Microphone access is required to place and receive calls.")
            .setPositiveButton("Retry", (dialog, which) -> {
                dialog.dismiss();
                requestMicrophonePermission();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
                JSObject ret = new JSObject();
                ret.put("granted", false);
                call.setKeepAlive(false);
                call.resolve(ret);
                pendingPermissionCall = null;
                awaitingSettingsResult = false;
                permissionAttemptCount = 0;
            })
            .setCancelable(false)
            .show();
    }

    private void showPermissionSettingsDialog(Activity activity) {
        Log.d(TAG, "showPermissionSettingsDialog");
        new AlertDialog.Builder(activity)
            .setTitle("Enable microphone")
            .setMessage("You can enable the microphone in Settings to use calling features.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                dialog.dismiss();
                ensureUnlockedThenOpenSettings();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
                handlePermissionFailure();
            })
            .setCancelable(false)
            .show();
    }

    private void showStandalonePermissionSettingsDialog(Activity activity, PluginCall call) {
        Log.d(TAG, "showStandalonePermissionSettingsDialog");
        new AlertDialog.Builder(activity)
            .setTitle("Enable microphone")
            .setMessage("Microphone access is required. Open Settings to enable the permission.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                dialog.dismiss();
                pendingPermissionCall = call;
                call.setKeepAlive(true);
                awaitingSettingsResult = true;
                ensureUnlockedThenOpenSettings();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
                JSObject ret = new JSObject();
                ret.put("granted", false);
                call.setKeepAlive(false);
                call.resolve(ret);
                pendingPermissionCall = null;
                awaitingSettingsResult = false;
                permissionAttemptCount = 0;
            })
            .setCancelable(false)
            .show();
    }

    private void openAppSettings() {
        Context context = getSafeContext();
        if (pendingPermissionCall != null || pendingPermissionAction != PendingPermissionAction.NONE) {
            awaitingSettingsResult = true;
        }
        Log.d(
            TAG,
            "openAppSettings: awaitingSettingsResult=" +
                awaitingSettingsResult +
                ", pendingAction=" +
                pendingPermissionAction +
                ", pendingCall=" +
                (pendingPermissionCall != null)
        );
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Toast.makeText(context, "Enable microphone permission and return to the app", Toast.LENGTH_LONG).show();
    }

    private boolean isDeviceLocked() {
        KeyguardManager km = (KeyguardManager) getSafeContext().getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    private void ensureUnlockedThenOpenSettings() {
        Activity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "ensureUnlockedThenOpenSettings: no activity available");
            openAppSettings();
            return;
        }

        KeyguardManager km = (KeyguardManager) getSafeContext().getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardLocked()) {
            openAppSettings();
            return;
        }

        km.requestDismissKeyguard(
            activity,
            new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    Log.d(TAG, "Keyguard dismissed, opening settings");
                    openAppSettings();
                }

                @Override
                public void onDismissCancelled() {
                    Log.d(TAG, "Keyguard dismissal cancelled");
                }

                @Override
                public void onDismissError() {
                    Log.w(TAG, "Keyguard dismissal error");
                }
            }
        );
    }

    private void moveAppToBackgroundIfLocked() {
        Activity activity = getActivity();
        if (activity != null && isDeviceLocked()) {
            Log.d(TAG, "moveAppToBackgroundIfLocked: moving task to back");
            activity.moveTaskToBack(true);
        }
    }

    private void handlePermissionFailure() {
        Log.d(
            TAG,
            "handlePermissionFailure: pendingAction=" + pendingPermissionAction + ", pendingCall=" + (pendingPermissionCall != null)
        );
        if (pendingPermissionAction == PendingPermissionAction.OUTGOING_CALL) {
            if (pendingOutgoingCall != null) {
                pendingOutgoingCall.setKeepAlive(false);
                pendingOutgoingCall.reject("Microphone permission is required to place a call.");
            }
            clearOutgoingPermissionState();
        } else if (pendingPermissionAction == PendingPermissionAction.ACCEPT_CALL) {
            if (pendingCallSidForPermission != null) {
                CallInvite invite = activeCallInvites.get(pendingCallSidForPermission);
                if (invite != null) {
                    dismissIncomingCallNotification();
                    activeCallInvites.remove(pendingCallSidForPermission);
                    try {
                        invite.reject(getSafeContext());
                    } catch (Exception ex) {
                        Log.w(TAG, "handlePermissionFailure: failed to reject invite", ex);
                    }
                }
                JSObject data = new JSObject();
                data.put("callSid", pendingCallSidForPermission);
                data.put("reason", "microphone_permission_denied");
                if (invite != null) {
                    if (invite.getFrom() != null) {
                        data.put("from", invite.getFrom().replace("client:", ""));
                    }
                    if (invite.getTo() != null) {
                        data.put("to", invite.getTo());
                    }
                }
                notifyListeners("callDisconnected", data);
            }
            pendingCallSidForPermission = null;
            pendingPermissionAction = PendingPermissionAction.NONE;
            awaitingSettingsResult = false;
            pendingPermissionCall = null;
            moveAppToBackgroundIfLocked();
        } else if (pendingPermissionCall != null) {
            JSObject ret = new JSObject();
            ret.put("granted", false);
            pendingPermissionCall.setKeepAlive(false);
            pendingPermissionCall.resolve(ret);
            pendingPermissionCall = null;
            awaitingSettingsResult = false;
        }
        permissionAttemptCount = 0;
    }

    private void clearOutgoingPermissionState() {
        pendingOutgoingCall = null;
        pendingOutgoingTo = null;
        if (pendingPermissionAction == PendingPermissionAction.OUTGOING_CALL) {
            pendingPermissionAction = PendingPermissionAction.NONE;
        }
        permissionAttemptCount = 0;
    }

    // Call parameter creation is now handled by VoiceCallService

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

        // Ensure microphone permission before starting the service
        ensureMicPermissionThenAccept(callSid);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
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

        callInvite.reject(getSafeContext());
        activeCallInvites.remove(callSid);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
        moveAppToBackgroundIfLocked();
    }

    @PluginMethod
    public void endCall(PluginCall call) {
        // End call via the foreground service
        Intent serviceIntent = new Intent(getSafeContext(), VoiceCallService.class);
        serviceIntent.setAction(VoiceCallService.ACTION_END_CALL);

        try {
            getSafeContext().startService(serviceIntent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error ending call via service", e);
            call.reject("Failed to end call: " + e.getMessage());
        }
    }

    @PluginMethod
    public void muteCall(PluginCall call) {
        boolean muted = call.getBoolean("muted", false);

        // Mute call via the foreground service
        Intent serviceIntent = new Intent(getSafeContext(), VoiceCallService.class);
        serviceIntent.setAction(VoiceCallService.ACTION_MUTE_CALL);
        serviceIntent.putExtra(VoiceCallService.EXTRA_MUTED, muted);

        try {
            getSafeContext().startService(serviceIntent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error muting call via service", e);
            call.reject("Failed to mute call: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setSpeaker(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);

        // Set speaker via the foreground service
        Intent serviceIntent = new Intent(getSafeContext(), VoiceCallService.class);
        serviceIntent.setAction(VoiceCallService.ACTION_SPEAKER_TOGGLE);
        serviceIntent.putExtra(VoiceCallService.EXTRA_SPEAKER_ENABLED, enabled);

        try {
            getSafeContext().startService(serviceIntent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error setting speaker via service", e);
            call.reject("Failed to set speaker: " + e.getMessage());
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

        // Build array of pending invites with same structure as callInviteReceived
        JSArray pendingInvitesArray = new JSArray();
        for (Map.Entry<String, CallInvite> entry : activeCallInvites.entrySet()) {
            String callSid = entry.getKey();
            CallInvite callInvite = entry.getValue();

            Map<String, String> params = callInvite.getCustomParameters();
            String callerName = params.containsKey("CapacitorTwilioCallerName")
                ? params.get("CapacitorTwilioCallerName")
                : callInvite.getFrom();

            // Strip "client:" prefix from caller name for consistency
            if (callerName != null && callerName.startsWith("client:")) {
                callerName = callerName.substring(7); // Remove "client:" prefix
            }

            JSObject inviteData = new JSObject();
            inviteData.put("callSid", callSid);
            inviteData.put("from", callerName);
            inviteData.put("to", callInvite.getTo());
            inviteData.put("customParams", new JSONObject(params));

            pendingInvitesArray.put(inviteData);
        }
        ret.put("pendingInvites", pendingInvitesArray);
        ret.put("activeCallsCount", activeCalls.size() + callsByUuid.size());
        call.resolve(ret);
    }

    @PluginMethod
    public void checkMicrophonePermission(PluginCall call) {
        boolean hasPermission =
            ActivityCompat.checkSelfPermission(getSafeContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        JSObject ret = new JSObject();
        ret.put("granted", hasPermission);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestMicrophonePermission(PluginCall call) {
        Log.d(TAG, "requestMicrophonePermission invoked");
        if (hasMicrophonePermission()) {
            Log.d(TAG, "requestMicrophonePermission: already granted");
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "requestMicrophonePermission: no activity available");
            call.reject("Unable to request permission without an active activity");
            return;
        }

        SharedPreferences prefs = getPrefs();
        boolean requestedBefore = prefs.getBoolean(PREF_MIC_PERMISSION_REQUESTED, false);
        boolean shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO);
        Log.d(
            TAG,
            "requestMicrophonePermission: requestedBefore=" +
                requestedBefore +
                ", shouldShow=" +
                shouldShow +
                ", pendingAction=" +
                pendingPermissionAction
        );

        if (pendingPermissionAction == PendingPermissionAction.NONE && !shouldShow && requestedBefore) {
            showStandalonePermissionSettingsDialog(activity, call);
            return;
        }

        prefs.edit().putBoolean(PREF_MIC_PERMISSION_REQUESTED, true).apply();

        if (pendingPermissionAction == PendingPermissionAction.NONE) {
            pendingPermissionCall = call;
            call.setKeepAlive(true);
        }
        awaitingSettingsResult = false;
        permissionAttemptCount++;
        permissionRequestTimestamp = System.currentTimeMillis();
        Log.d(TAG, "requestMicrophonePermission: invoking ActivityCompat.requestPermissions (attempt " + permissionAttemptCount + ")");

        ActivityCompat.requestPermissions(
            activity,
            new String[] { Manifest.permission.RECORD_AUDIO },
            REQUEST_CODE_RECORD_AUDIO_FOR_ACCEPT
        );
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_RECORD_AUDIO_FOR_ACCEPT) {
            return;
        }

        boolean granted = hasMicrophonePermission();
        Log.d(
            TAG,
            "handleRequestPermissionsResult: granted=" +
                granted +
                ", attempt=" +
                permissionAttemptCount +
                ", pendingAction=" +
                pendingPermissionAction +
                ", standaloneCall=" +
                (pendingPermissionCall != null)
        );
        if (granted) {
            handleMicrophonePermissionGranted();
        } else {
            handleMicrophonePermissionDenied();
        }
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
        public void onError(@NonNull RegistrationException registrationException, @NonNull String accessToken, @NonNull String fcmToken) {
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
        public void onError(@NonNull RegistrationException registrationException, @NonNull String accessToken, @NonNull String fcmToken) {
            Log.e(TAG, "Unregistration error: " + registrationException.getMessage());
        }
    };

    // Call handling is now done by VoiceCallService
    /*private final Call.Listener callListener = new Call.Listener() {
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
    };*/

    private void showIncomingCallNotification(CallInvite callInvite, String callSid, String callerName) {
        try {
            // Create intent for accepting the call
            PendingIntent acceptPendingIntent;
            if (this.bridge == null) {
                // App NOT running - launch new activity
                Intent acceptIntent = new Intent(getSafeContext(), mainActivityClass);
                acceptIntent.setAction(ACTION_ACCEPT_CALL);
                acceptIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                acceptIntent.putExtra("AUTO_ACCEPT_CALL", true);
                acceptIntent.putExtra(EXTRA_CALL_SID, callSid);
                acceptPendingIntent = PendingIntent.getActivity(
                    getSafeContext(),
                    0,
                    acceptIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                // App IS running - use BroadcastReceiver to communicate with existing activity
                Intent acceptIntent = new Intent(getSafeContext(), NotificationActionReceiver.class);
                acceptIntent.setAction(ACTION_ACCEPT_CALL);
                acceptIntent.putExtra(EXTRA_CALL_SID, callSid);
                acceptPendingIntent = PendingIntent.getBroadcast(
                    getSafeContext(),
                    0,
                    acceptIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            }

            // Create intent for rejecting the call
            Intent rejectIntent = new Intent(getSafeContext(), NotificationActionReceiver.class);
            rejectIntent.setAction(ACTION_REJECT_CALL);
            rejectIntent.putExtra(EXTRA_CALL_SID, callSid);
            PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(
                getSafeContext(),
                1,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Create intent for full screen
            Intent fullScreenIntent = new Intent(getSafeContext(), mainActivityClass);
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            fullScreenIntent.putExtra("INCOMING_CALL", true);
            fullScreenIntent.putExtra(EXTRA_CALL_SID, callSid);
            fullScreenIntent.putExtra("CALLER_NAME", callerName);
            fullScreenIntent.putExtra("CALL_FROM", callInvite.getFrom());
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                getSafeContext(),
                2,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Create Person object for the caller
            androidx.core.app.Person caller = new androidx.core.app.Person.Builder().setName(callerName).setImportant(true).build();

            // Create CallStyle notification with proper colored buttons
            NotificationCompat.CallStyle callStyle = NotificationCompat.CallStyle.forIncomingCall(
                caller, // person (caller as Person object)
                rejectPendingIntent, // decline intent
                acceptPendingIntent // answer intent
            )
                .setAnswerButtonColorHint(0xFF4CAF50) // Green color for accept button
                .setDeclineButtonColorHint(0xFFF44336); // Red color for reject button

            // Build notification with CallStyle
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getSafeContext(), NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Incoming Call")
                .setContentText(callerName + " is calling")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setStyle(callStyle)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setTimeoutAfter(30000); // Auto-dismiss after 30 seconds

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getSafeContext());
            if (
                ActivityCompat.checkSelfPermission(getSafeContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Cannot get POST_NOTIFICATION perm");
                return;
            } else {
                notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build());
            }

            Log.d(TAG, "Incoming call notification shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage(), e);
        }
    }

    private void dismissIncomingCallNotification() {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getSafeContext());
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

        Map<String, String> params = callInvite.getCustomParameters();
        String callerName = params.containsKey("CapacitorTwilioCallerName")
            ? params.get("CapacitorTwilioCallerName")
            : callInvite.getFrom();

        // Create and show notification
        showIncomingCallNotification(callInvite, callSid, callerName);

        // Start ringtone and vibration
        startRingtone();

        // Strip "client:" prefix from caller name for consistency
        String fromValue = callerName;
        if (fromValue != null && fromValue.startsWith("client:")) {
            fromValue = fromValue.substring(7); // Remove "client:" prefix
        }

        JSObject data = new JSObject();
        data.put("callSid", callSid);
        data.put("from", fromValue);
        data.put("to", callInvite.getTo());
        data.put("customParams", new JSONObject(params));
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
            ensureMicPermissionThenAccept(callSid);
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

            callInvite.reject(getSafeContext());
            activeCallInvites.remove(callSid);

            // Notify JavaScript that the call was rejected from notification
            JSObject data = new JSObject();
            data.put("callSid", callSid);
            data.put("from", callInvite.getFrom());
            data.put("rejectedFromNotification", true);
            notifyListeners("callDisconnected", data);

            Log.d(TAG, "Call rejected from notification");
            moveAppToBackgroundIfLocked();
        } else {
            Log.e(TAG, "Call invite not found for SID: " + callSid);
        }
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }
}
