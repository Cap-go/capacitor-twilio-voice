package ee.forgr.capacitor_twilio_voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationAction";
    private static final String ACTION_ACCEPT_CALL = "ACTION_ACCEPT_CALL";
    private static final String ACTION_REJECT_CALL = "ACTION_REJECT_CALL";
    private static final String EXTRA_CALL_SID = "EXTRA_CALL_SID";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String callSid = intent.getStringExtra(EXTRA_CALL_SID);
        
        Log.d(TAG, "Received notification action: " + action + " for call: " + callSid);
        
        CapacitorTwilioVoicePlugin plugin = CapacitorTwilioVoicePlugin.getInstance();
        if (plugin == null) {
            Log.e(TAG, "Plugin instance not available");
            return;
        }
        
        if (ACTION_ACCEPT_CALL.equals(action)) {
            plugin.acceptCallFromNotification(callSid);
        } else if (ACTION_REJECT_CALL.equals(action)) {
            plugin.rejectCallFromNotification(callSid);
        }
    }
} 