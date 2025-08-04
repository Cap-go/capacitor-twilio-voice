package ee.forgr.capacitor_twilio_voice;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService implements MessageListener {
    private static final String TAG = "VoiceFirebaseService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VoiceFirebaseMessagingService created");
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Token refresh is handled in the plugin's initializeFCM method
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, String.format(
                "Received Firebase message\n\tmessage data: %s\n\tfrom: %s",
                remoteMessage.getData(),
                remoteMessage.getFrom()));

        // Check if message contains a data payload and handle with Twilio Voice SDK
        if (!remoteMessage.getData().isEmpty() && 
            !Voice.handleMessage(this, remoteMessage.getData(), this)) {
            Log.w(TAG, String.format(
                "Received message was not a valid Twilio Voice SDK payload: %s", 
                remoteMessage.getData()));
        }
    }

    // MessageListener implementation
    @Override
    public void onCallInvite(@NonNull CallInvite callInvite) {
        Log.d(TAG, "Received call invite from: " + callInvite.getFrom());
        handleIncomingCallInvite(callInvite);
    }

    @Override
    public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite,
                                     @Nullable CallException callException) {
        Log.d(TAG, "Received cancelled call invite");
        handleCancelledCallInvite(cancelledCallInvite);
    }

    private void handleIncomingCallInvite(CallInvite callInvite) {
        Log.d(TAG, "Handling incoming call invite from: " + callInvite.getFrom());

        // Get the plugin instance and handle the call invite
        CapacitorTwilioVoicePlugin plugin = CapacitorTwilioVoicePlugin.getInstance();
        if (plugin != null) {
            plugin.handleCallInvite(callInvite);
        } else {
            Log.w(TAG, "Plugin instance not available to handle call invite");
        }
    }

    private void handleCancelledCallInvite(CancelledCallInvite cancelledCallInvite) {
        Log.d(TAG, "Handling cancelled call invite");

        // Get the plugin instance and handle the cancelled call invite
        CapacitorTwilioVoicePlugin plugin = CapacitorTwilioVoicePlugin.getInstance();
        if (plugin != null) {
            plugin.handleCancelledCallInvite(cancelledCallInvite);
        } else {
            Log.w(TAG, "Plugin instance not available to handle cancelled call invite");
        }
    }


}