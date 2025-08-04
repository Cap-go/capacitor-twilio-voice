package com.example.plugin;

import android.app.Application;

import ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin;


public class CapacitorApplication extends Application {
    @Override
    public void onCreate() {
        CapacitorTwilioVoicePlugin.instance = new CapacitorTwilioVoicePlugin();
        super.onCreate();
    }
}
