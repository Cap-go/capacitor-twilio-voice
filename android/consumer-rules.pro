# R8 consumer rules for @capgo/capacitor-twilio-voice (merged into apps that depend on this AAR)

# Capacitor loads @PluginMethod entry points and registers @CapacitorPlugin classes via reflection
# Keep annotation metadata Capacitor and WebView read through reflection.
-keepattributes RuntimeVisibleAnnotations

-keep class ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin {
    <init>();
    @com.getcapacitor.PluginMethod <methods>;
}
