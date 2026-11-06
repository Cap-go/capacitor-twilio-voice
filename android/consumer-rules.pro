# R8 consumer rules for @capgo/capacitor-twilio-voice (merged into apps that depend on this AAR)

# Capacitor loads @PluginMethod entry points and registers @CapacitorPlugin classes via reflection
-keep class ee.forgr.capacitor_twilio_voice.CapacitorTwilioVoicePlugin {
    <init>();
    @com.getcapacitor.PluginMethod <methods>;
}
