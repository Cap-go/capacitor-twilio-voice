//
//  CustomCapacitorViewController.swift
//  App
//
//  Created by Michał Tremblay on 31/07/2025.
//

import Capacitor
import CapgoCapacitorTwilioVoice

// Note: edit Main.storyboard for this to work

class CustomCapacitorViewController: CAPBridgeViewController {
    
    var passPushKitEventDelegate: ((PushKitEventDelegate?) -> Void)? = nil
    
    override func capacitorDidLoad() {
        if let passPushKitEventDelegate = passPushKitEventDelegate {
            guard let bridge = self.bridge else {
                fatalError("bridge is not initialized")
            }
            
            guard let twilioPlugin = bridge.plugin(withName: "CapacitorTwilioVoice") as? CapacitorTwilioVoicePlugin else {
                fatalError("Twilio plugin is not installed")
            }
            
            passPushKitEventDelegate(twilioPlugin)
        }
    }
}
