package ee.forgr.capacitor_twilio_voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.Voice;
import java.util.List;

public class VoiceCallService extends Service {

    private static final String TAG = "VoiceCallService";
    private static final String VOICE_CHANNEL_ID = "voice_call_channel";
    private static final int VOICE_NOTIFICATION_ID = 12345;

    // Service actions
    public static final String ACTION_START_CALL = "START_CALL";
    public static final String ACTION_ACCEPT_CALL = "ACCEPT_CALL";
    public static final String ACTION_END_CALL = "END_CALL";
    public static final String ACTION_MUTE_CALL = "MUTE_CALL";
    public static final String ACTION_SPEAKER_TOGGLE = "SPEAKER_TOGGLE";

    // Intent extras
    public static final String EXTRA_CALL_TO = "CALL_TO";
    public static final String EXTRA_ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String EXTRA_CALL_INVITE = "CALL_INVITE";
    public static final String EXTRA_CALL_SID = "CALL_SID";
    public static final String EXTRA_MUTED = "MUTED";
    public static final String EXTRA_SPEAKER_ENABLED = "SPEAKER_ENABLED";

    private Call activeCall;
    private CallInvite activeCallInvite;
    private AudioSwitch audioSwitch;
    private boolean isCallMuted = false;
    private boolean isSpeakerEnabled = false;
    private String currentCallSid;
    private VoiceCallServiceListener serviceListener;

    public interface VoiceCallServiceListener {
        void onCallConnected(Call call);
        void onCallDisconnected(Call call, CallException error);
        void onCallRinging(Call call);
        void onCallReconnecting(Call call, CallException error);
        void onCallReconnected(Call call);
        void onCallQualityWarningsChanged(
            Call call,
            java.util.Set<Call.CallQualityWarning> currentWarnings,
            java.util.Set<Call.CallQualityWarning> previousWarnings
        );
        void onCallInviteAccepted(CallInvite callInvite);
    }

    public class VoiceCallBinder extends Binder {

        public VoiceCallService getService() {
            return VoiceCallService.this;
        }
    }

    private final IBinder binder = new VoiceCallBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VoiceCallService created");

        createNotificationChannel();
        initializeAudioSwitch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "VoiceCallService onStartCommand: " + (intent != null ? intent.getAction() : "null"));

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_CALL:
                    handleStartCall(intent);
                    break;
                case ACTION_ACCEPT_CALL:
                    handleAcceptCall(intent);
                    break;
                case ACTION_END_CALL:
                    handleEndCall();
                    break;
                case ACTION_MUTE_CALL:
                    handleMuteCall(intent);
                    break;
                case ACTION_SPEAKER_TOGGLE:
                    handleSpeakerToggle(intent);
                    break;
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    break;
            }
        }

        // Return START_NOT_STICKY so the service doesn't restart if killed
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "VoiceCallService destroyed");

        // Clean up active call
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }

        // Clean up audio switch
        if (audioSwitch != null) {
            audioSwitch.stop();
            audioSwitch = null;
        }

        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(VOICE_CHANNEL_ID, "Voice Calls", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Ongoing voice calls");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeAudioSwitch() {
        audioSwitch = new AudioSwitch(getApplicationContext());
        audioSwitch.start((audioDevices, selectedDevice) -> {
            Log.d(TAG, "Available audio devices: " + audioDevices);
            Log.d(TAG, "Selected audio device: " + selectedDevice);
            return kotlin.Unit.INSTANCE;
        });
    }

    public void setServiceListener(VoiceCallServiceListener listener) {
        this.serviceListener = listener;
    }

    private void handleStartCall(Intent intent) {
        String to = intent.getStringExtra(EXTRA_CALL_TO);
        String accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN);

        if (accessToken == null || accessToken.isEmpty()) {
            Log.e(TAG, "Cannot start call - no access token provided");
            stopSelf();
            return;
        }

        Log.d(TAG, "Starting outgoing call to: " + to);

        // Start foreground service with ongoing call notification
        startForeground(VOICE_NOTIFICATION_ID, createOngoingCallNotification("Connecting...", false));

        ConnectOptions.Builder builder = new ConnectOptions.Builder(accessToken);
        if (to != null && !to.isEmpty()) {
            builder.params(java.util.Collections.singletonMap("to", to));
        }

        activeCall = Voice.connect(this, builder.build(), callListener);
        if (activeCall != null) {
            currentCallSid = activeCall.getSid();
            Log.d(TAG, "Call initiated with SID: " + currentCallSid);
        }
    }

    private void handleAcceptCall(Intent intent) {
        // This would be called when accepting from notification or plugin
        CallInvite callInvite = intent.getParcelableExtra(EXTRA_CALL_INVITE);
        String accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN);

        if (callInvite != null && accessToken != null) {
            Log.d(TAG, "Accepting incoming call from: " + callInvite.getFrom());

            // Start foreground service
            startForeground(VOICE_NOTIFICATION_ID, createOngoingCallNotification("Accepting call...", false));

            activeCallInvite = callInvite;
            activeCall = callInvite.accept(this, callListener);
            if (activeCall != null) {
                currentCallSid = activeCall.getSid();
                Log.d(TAG, "Call accepted with SID: " + currentCallSid);
            }

            if (serviceListener != null) {
                serviceListener.onCallInviteAccepted(callInvite);
            }
        }
    }

    private void handleEndCall() {
        Log.d(TAG, "Ending call");

        if (activeCall != null) {
            activeCall.disconnect();
            // The callListener.onDisconnected will handle cleanup
        } else {
            // No active call, just stop the service
            stopForeground(true);
            stopSelf();
        }
    }

    private void handleMuteCall(Intent intent) {
        boolean muted = intent.getBooleanExtra(EXTRA_MUTED, false);

        if (activeCall != null) {
            activeCall.mute(muted);
            isCallMuted = muted;

            // Update ongoing notification
            updateOngoingCallNotification();

            Log.d(TAG, "Call " + (muted ? "muted" : "unmuted"));
        }
    }

    private void handleSpeakerToggle(Intent intent) {
        boolean speakerEnabled = intent.getBooleanExtra(EXTRA_SPEAKER_ENABLED, false);

        if (audioSwitch != null) {
            List<AudioDevice> audioDevices = audioSwitch.getAvailableAudioDevices();
            AudioDevice selectedDevice = null;

            if (speakerEnabled) {
                // Find speakerphone
                for (AudioDevice device : audioDevices) {
                    if (device instanceof AudioDevice.Speakerphone) {
                        selectedDevice = device;
                        break;
                    }
                }
            } else {
                // Find earpiece or bluetooth
                for (AudioDevice device : audioDevices) {
                    if (device instanceof AudioDevice.Earpiece || device instanceof AudioDevice.BluetoothHeadset) {
                        selectedDevice = device;
                        break;
                    }
                }
            }

            if (selectedDevice != null) {
                audioSwitch.selectDevice(selectedDevice);
                isSpeakerEnabled = speakerEnabled;
                Log.d(TAG, "Audio device changed to: " + selectedDevice.getName());
            }
        }
    }

    private Notification createOngoingCallNotification(String contentText, boolean showActions) {
        // Create intent for opening the app
        Intent openAppIntent = new Intent(this, getMainActivityClass());
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
            .setSmallIcon(getDrawableId("ic_notification_call"))
            .setContentTitle("🔊 Ongoing Call")
            .setContentText(contentText)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(0xFF2196F3) // Beautiful blue color
            .setContentIntent(openAppPendingIntent);

        if (showActions && activeCall != null) {
            // Add mute/unmute action with beautiful styling
            Intent muteIntent = new Intent(this, VoiceCallService.class);
            muteIntent.setAction(ACTION_MUTE_CALL);
            muteIntent.putExtra(EXTRA_MUTED, !isCallMuted);
            PendingIntent mutePendingIntent = PendingIntent.getService(
                this,
                1,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Beautiful mute/unmute action with icons
            String muteText = isCallMuted ? "🔊 Unmute" : "🔇 Mute";
            builder.addAction(
                android.R.drawable.ic_media_pause, // Use system microphone icon
                muteText,
                mutePendingIntent
            );

            // Add beautiful end call action with red color hint
            Intent endIntent = new Intent(this, VoiceCallService.class);
            endIntent.setAction(ACTION_END_CALL);
            PendingIntent endPendingIntent = PendingIntent.getService(
                this,
                2,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Beautiful end call action with reject icon
            builder.addAction(getDrawableId("ic_phone_reject"), "📞 End Call", endPendingIntent);
        }

        return builder.build();
    }

    private int getDrawableId(String drawableName) {
        try {
            return getResources().getIdentifier(drawableName, "drawable", getPackageName());
        } catch (Exception e) {
            Log.w(TAG, "Could not find drawable: " + drawableName + ", using default");
            return android.R.drawable.ic_menu_call;
        }
    }

    private void updateOngoingCallNotification() {
        if (activeCall != null) {
            String statusText = "Connected";
            if (isCallMuted) {
                statusText += " (Muted)";
            }

            Notification notification = createOngoingCallNotification(statusText, true);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(VOICE_NOTIFICATION_ID, notification);
            }
        }
    }

    private Class<?> getMainActivityClass() {
        // Get the main activity class from the application
        String packageName = getPackageName();
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null && launchIntent.getComponent() != null) {
            try {
                return Class.forName(launchIntent.getComponent().getClassName());
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Could not find main activity class", e);
            }
        }

        // Fallback - assume standard Capacitor activity name
        try {
            return Class.forName(packageName + ".MainActivity");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Could not find MainActivity", e);
            return null;
        }
    }

    // Call listener for handling call events
    private final Call.Listener callListener = new Call.Listener() {
        @Override
        public void onConnected(Call call) {
            Log.d(TAG, "Call connected: " + call.getSid());
            activeCall = call;
            currentCallSid = call.getSid();

            // Update notification to show connected state with actions
            updateOngoingCallNotification();

            if (serviceListener != null) {
                serviceListener.onCallConnected(call);
            }
        }

        @Override
        public void onConnectFailure(Call call, CallException error) {
            Log.e(TAG, "Call connect failure: " + call.getSid() + (error != null ? " Error: " + error.getMessage() : ""));

            activeCall = null;
            currentCallSid = null;

            if (serviceListener != null) {
                serviceListener.onCallDisconnected(call, error);
            }

            // Stop foreground service on failure
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onReconnecting(Call call, CallException callException) {
            Log.d(TAG, "Call reconnecting: " + call.getSid());

            if (serviceListener != null) {
                serviceListener.onCallReconnecting(call, callException);
            }
        }

        @Override
        public void onReconnected(Call call) {
            Log.d(TAG, "Call reconnected: " + call.getSid());

            if (serviceListener != null) {
                serviceListener.onCallReconnected(call);
            }
        }

        @Override
        public void onDisconnected(Call call, CallException error) {
            Log.d(TAG, "Call disconnected: " + call.getSid() + (error != null ? " Error: " + error.getMessage() : ""));

            activeCall = null;
            currentCallSid = null;
            isCallMuted = false;
            isSpeakerEnabled = false;

            if (serviceListener != null) {
                serviceListener.onCallDisconnected(call, error);
            }

            // Stop foreground service
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onCallQualityWarningsChanged(
            Call call,
            java.util.Set<Call.CallQualityWarning> currentWarnings,
            java.util.Set<Call.CallQualityWarning> previousWarnings
        ) {
            Log.d(TAG, "Call quality warnings changed for: " + call.getSid());

            if (serviceListener != null) {
                serviceListener.onCallQualityWarningsChanged(call, currentWarnings, previousWarnings);
            }
        }

        @Override
        public void onRinging(Call call) {
            Log.d(TAG, "Call ringing: " + call.getSid());

            // Update notification to show ringing state
            Notification notification = createOngoingCallNotification("Ringing...", false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(VOICE_NOTIFICATION_ID, notification);
            }

            if (serviceListener != null) {
                serviceListener.onCallRinging(call);
            }
        }
    };

    // Public methods for controlling the call
    public Call getActiveCall() {
        return activeCall;
    }

    public String getCurrentCallSid() {
        return currentCallSid;
    }

    public boolean isCallMuted() {
        return isCallMuted;
    }

    public boolean isSpeakerEnabled() {
        return isSpeakerEnabled;
    }
}
