package ee.forgr.capacitor_twilio_voice;

import com.getcapacitor.Logger;

public class CapacitorTwilioVoice {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
