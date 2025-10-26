package app.capgo.twilio.voice;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSExport;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginHandle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.bridge.registerPluginInstance(CapacitorTwilioVoicePlugin.getInstance());

        try {
            Field pluginsField = this.bridge.getClass().getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            Map<String, PluginHandle> plugins = (Map<String, PluginHandle>) pluginsField.get(this.bridge);
            ArrayList<PluginHandle> pluginHandles = new ArrayList<>(plugins.values());
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
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
