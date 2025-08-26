package com.example.plugin;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSExport;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginHandle;

import java.util.ArrayList;
import java.util.Collections;

import ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.bridge.registerPluginInstance(CapacitorTwilioVoicePlugin.getInstance());
        ArrayList<PluginHandle> pluginHandles = new ArrayList<>();
        pluginHandles.add(this.bridge.getPlugin("CapacitorTwilioVoice"));
        String pluginJS = JSExport.getPluginJS(pluginHandles);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            String allowedOrigin = Uri.parse(this.bridge.getAppUrl()).buildUpon().path(null).fragment(null).clearQuery().build().toString();
            try {
                WebViewCompat.addDocumentStartJavaScript(this.getBridge().getWebView(), pluginJS, Collections.singleton(allowedOrigin));
            } catch (IllegalArgumentException ex) {
                Logger.warn("Invalid url, using fallback");
            }
        }
    }
}
