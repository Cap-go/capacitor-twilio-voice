import { CapacitorTwilioVoice } from '@capgo/capacitor-twilio-voice';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    CapacitorTwilioVoice.echo({ value: inputValue })
}
