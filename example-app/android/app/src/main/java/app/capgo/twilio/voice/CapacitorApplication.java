package app.capgo.twilio.voice;

import android.app.Application;

import ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin;


public class CapacitorApplication extends Application {
    @Override
    public void onCreate() {
        CapacitorTwilioVoicePlugin.instance = new CapacitorTwilioVoicePlugin();
        CapacitorTwilioVoicePlugin.instance.setInjectedContext(this);
        CapacitorTwilioVoicePlugin.instance.setMainActivityClass(MainActivity.class);
        super.onCreate();
    }
}
