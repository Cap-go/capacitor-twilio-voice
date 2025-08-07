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

        if (ACTION_ACCEPT_CALL.equals(action)) {
            if (plugin == null) {
                Log.d(TAG, "App not running, cannot accept call directly");
                return;
            }
            plugin.acceptCallFromNotification(callSid);
        } else if (ACTION_REJECT_CALL.equals(action)) {
            if (plugin == null) {
                // App is not running, but we can still reject the call
                // We'll need to handle this differently - maybe just dismiss notification
                Log.d(TAG, "App not running, cannot reject call directly");
                return;
            }
            plugin.rejectCallFromNotification(callSid);
        }
    }

    private void launchAppToAcceptCall(Context context, String callSid) {
        try {
            // Get the main activity class name from the application context
            String packageName = context.getPackageName();
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);

            if (launchIntent != null) {
                // Add flags to ensure app comes to foreground and clears any existing instances
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                // Add extra data to indicate this is an incoming call acceptance
                launchIntent.putExtra("AUTO_ACCEPT_CALL", true);
                launchIntent.putExtra(EXTRA_CALL_SID, callSid);

                Log.d(TAG, "Launching app with auto-accept for call: " + callSid);
                context.startActivity(launchIntent);
            } else {
                Log.e(TAG, "Could not get launch intent for package: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching app to accept call", e);
        }
    }
}
