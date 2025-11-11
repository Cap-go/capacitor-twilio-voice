import Foundation
import Capacitor
import PushKit
import CallKit
import TwilioVoice
import AVFoundation
import Intents

let kRegistrationTTLInDays = 365
let kCachedDeviceToken = "CachedDeviceToken"
let kCachedBindingDate = "CachedBindingDate"
let kCachedAccessToken = "CachedAccessToken"
let twimlParamTo = "to"

public protocol PushKitEventDelegate: AnyObject {
    func credentialsUpdated(credentials: PKPushCredentials)
    func credentialsInvalidated()
    func incomingPushReceived(payload: PKPushPayload)
    func incomingPushReceived(payload: PKPushPayload, completion: @escaping () -> Void)
}

/**
 * Capacitor Twilio Voice Plugin
 *
 * Authentication via login() method with JWT access tokens.
 * Automatically validates token expiration and stores tokens persistently.
 */
@objc(CapacitorTwilioVoicePlugin)
public class CapacitorTwilioVoicePlugin: CAPPlugin, CAPBridgedPlugin, PushKitEventDelegate {
    private let pluginVersion: String = "7.7.2"

    public let identifier = "CapacitorTwilioVoicePlugin"
    public let jsName = "CapacitorTwilioVoice"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "login", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "logout", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isLoggedIn", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "makeCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "acceptCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rejectCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "muteCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setSpeaker", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "getCallStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkMicrophonePermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestMicrophonePermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]

    // MARK: - Properties
    private var accessToken: String!
    private var incomingPushCompletionCallback: (() -> Void)?
    private var activeCallInvites: [String: CallInvite] = [:]
    private var activeCalls: [String: Call] = [:]
    private var activeCall: Call?
    private var callKitProvider: CXProvider?
    private let callKitCallController = CXCallController()
    private var userInitiatedDisconnect: Bool = false
    private var audioDevice = DefaultAudioDevice()
    private var callKitCompletionCallback: ((Bool) -> Void)?
    private var playCustomRingback = false
    private var ringtonePlayer: AVAudioPlayer?
    private struct PendingOutgoingCall {
        let to: String
        let completion: (Bool) -> Void
        let isSystemInitiated: Bool
        let displayName: String?
    }
    private var pendingOutgoingCalls: [UUID: PendingOutgoingCall] = [:]

    deinit {
        // Remove observers
        NotificationCenter.default.removeObserver(self)

        // CallKit has an odd API contract where the developer must call invalidate or the CXProvider is leaked.
        if let provider = callKitProvider {
            provider.invalidate()
        }
    }

    override public func load() {
        super.load()

        // Try to load and validate stored access token
        if let storedToken = UserDefaults.standard.string(forKey: kCachedAccessToken),
           isTokenValid(storedToken) {
            self.accessToken = storedToken
            performRegistration()
        } else {
            NSLog("No valid access token found. Please call login() first.")
        }

        setupAudioSession()
        setupCallKit()
        setupAudioDevice()
        setupNotifications()
    }

    private func setupCallKit() {
        let configuration = CXProviderConfiguration(localizedName: "Voice Call")
        configuration.maximumCallGroups = 2
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportedHandleTypes = [.generic, .phoneNumber]
        configuration.includesCallsInRecents = true
        callKitProvider = CXProvider(configuration: configuration)
        if let provider = callKitProvider {
            provider.setDelegate(self, queue: nil)
        }
    }

    private func setupAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord,
                                         mode: .voiceChat,
                                         options: [.allowBluetooth, .allowBluetoothA2DP, .allowAirPlay])
            try audioSession.setActive(true)
            NSLog("Audio session configured successfully")
        } catch {
            NSLog("Failed to configure audio session: \(error.localizedDescription)")
        }
    }

    private func setupAudioDevice() {
        TwilioVoiceSDK.audioDevice = audioDevice

        // Set default audio routing to earpiece (not speaker)
        toggleAudioRoute(toSpeaker: false)
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMediaServicesReset),
            name: AVAudioSession.mediaServicesWereResetNotification,
            object: nil
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioSessionInterruption),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
    }

    @objc private func handleMediaServicesReset() {
        NSLog("Media services were reset, reconfiguring audio session")
        setupAudioSession()

        // Re-enable audio device
        audioDevice.isEnabled = true

        // Notify listeners about the reset
        notifyListeners("audioSessionReset", data: nil)
    }

    @objc private func handleAudioSessionInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        switch type {
        case .began:
            NSLog("Audio session interruption began")
            audioDevice.isEnabled = false
            notifyListeners("audioSessionInterrupted", data: ["type": "began"])

        case .ended:
            NSLog("Audio session interruption ended")

            // Check if we should resume
            if let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt {
                let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
                if options.contains(.shouldResume) {
                    do {
                        try AVAudioSession.sharedInstance().setActive(true)
                        audioDevice.isEnabled = true
                        NSLog("Audio session resumed after interruption")
                        notifyListeners("audioSessionResumed", data: nil)
                    } catch {
                        NSLog("Failed to resume audio session: \(error.localizedDescription)")
                    }
                }
            }

            notifyListeners("audioSessionInterrupted", data: ["type": "ended"])

        @unknown default:
            break
        }
    }

    private func isTokenValid(_ token: String) -> Bool {
        guard let payload = decodeJWTPayload(token) else {
            NSLog("Failed to decode JWT payload")
            return false
        }

        guard let exp = payload["exp"] as? TimeInterval else {
            NSLog("JWT does not contain exp claim")
            return false
        }

        let currentTime = Date().timeIntervalSince1970
        let isValid = exp > currentTime

        if !isValid {
            NSLog("JWT token has expired. Exp: \(exp), Current: \(currentTime)")
        }

        return isValid
    }

    private func decodeJWTPayload(_ token: String) -> [String: Any]? {
        let parts = token.components(separatedBy: ".")
        guard parts.count == 3 else {
            NSLog("Invalid JWT format")
            return nil
        }

        var base64 = parts[1]
        // Add padding if needed
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 = base64.padding(toLength: base64.count + 4 - remainder, withPad: "=", startingAt: 0)
        }

        guard let data = Data(base64Encoded: base64) else {
            NSLog("Failed to decode base64 JWT payload")
            return nil
        }

        do {
            let json = try JSONSerialization.jsonObject(with: data, options: [])
            return json as? [String: Any]
        } catch {
            NSLog("Failed to parse JWT payload JSON: \(error)")
            return nil
        }
    }

    private func performRegistration() {
        guard let accessToken = self.accessToken else {
            NSLog("No access token available. Cannot perform registration.")
            return
        }

        guard isTokenValid(accessToken) else {
            NSLog("Access token has expired. Cannot perform registration.")
            notifyListeners("registrationFailure", data: ["error": "Access token has expired"])
            return
        }

        guard let deviceToken = UserDefaults.standard.data(forKey: kCachedDeviceToken) else {
            NSLog("No device token available. Registration will be performed when push credentials are received.")
            return
        }

        TwilioVoiceSDK.register(accessToken: accessToken, deviceToken: deviceToken) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    NSLog("Registration failed: \(error.localizedDescription)")
                    self?.notifyListeners("registrationFailure", data: ["error": error.localizedDescription])
                } else {
                    NSLog("Successfully registered for VoIP push notifications.")
                    UserDefaults.standard.set(Date(), forKey: kCachedBindingDate)
                    self?.notifyListeners("registrationSuccess", data: nil)
                }
            }
        }
    }

    // MARK: - Plugin Methods

    @objc func login(_ call: CAPPluginCall) {
        guard let accessToken = call.getString("accessToken") else {
            call.reject("accessToken is required")
            return
        }

        // Validate the JWT token
        guard isTokenValid(accessToken) else {
            call.reject("Invalid or expired access token")
            return
        }

        // Store the token persistently
        UserDefaults.standard.set(accessToken, forKey: kCachedAccessToken)
        self.accessToken = accessToken

        NSLog("Access token stored and validated successfully")

        // Perform registration with the new token
        performRegistration()

        call.resolve(["success": true])
    }

    @objc func logout(_ call: CAPPluginCall) {
        NSLog("Logging out and clearing stored credentials")

        // Unregister from VoIP if we have a device token and access token
        if let accessToken = self.accessToken,
           let deviceToken = UserDefaults.standard.data(forKey: kCachedDeviceToken) {
            TwilioVoiceSDK.unregister(accessToken: accessToken, deviceToken: deviceToken) { error in
                if let error = error {
                    NSLog("Error during unregistration: \(error.localizedDescription)")
                } else {
                    NSLog("Successfully unregistered from VoIP push notifications")
                }
            }
        }

        // Clear stored credentials
        UserDefaults.standard.removeObject(forKey: kCachedAccessToken)
        UserDefaults.standard.removeObject(forKey: kCachedBindingDate)

        // Clear instance variables
        self.accessToken = nil

        // End any active calls
        for (_, call) in activeCalls {
            call.disconnect()
        }
        activeCalls.removeAll()
        activeCallInvites.removeAll()
        activeCall = nil

        NSLog("Logout completed successfully")
        call.resolve(["success": true])
    }

    @objc func isLoggedIn(_ call: CAPPluginCall) {
        var isLoggedIn = false
        var identity: String?

        if let storedToken = UserDefaults.standard.string(forKey: kCachedAccessToken) {
            isLoggedIn = isTokenValid(storedToken)

            if isLoggedIn, let payload = decodeJWTPayload(storedToken) {
                // Extract identity from grants.identity
                if let grants = payload["grants"] as? [String: Any] {
                    identity = grants["identity"] as? String
                }
            }
        }

        var response: [String: Any] = [
            "isLoggedIn": isLoggedIn,
            "hasValidToken": isLoggedIn
        ]

        if let identity = identity {
            response["identity"] = identity
        }

        call.resolve(response)
    }

    @objc func makeCall(_ call: CAPPluginCall) {
        guard let accessToken = self.accessToken else {
            call.reject("No access token available. Please call login() first.")
            return
        }

        guard isTokenValid(accessToken) else {
            call.reject("Access token has expired. Please call login() with a new token.")
            return
        }

        guard let to = call.getString("to") else {
            call.reject("to parameter is required")
            return
        }

        checkRecordPermission { [weak self] permissionGranted in
            guard permissionGranted else {
                call.reject("Microphone permission not granted")
                return
            }

            let uuid = UUID()
            self?.performStartCallAction(uuid: uuid,
                                         handle: to,
                                         to: to,
                                         isSystemInitiated: false,
                                         completion: { success in
                                            if success {
                                                call.resolve(["success": true, "callSid": uuid.uuidString])
                                            } else {
                                                call.reject("Failed to start call")
                                            }
                                         })
        }
    }

    @available(iOS 10.0, *)
    public func handleStartCallIntent(intent: INIntent) {
        if #available(iOS 13.0, *), let callIntent = intent as? INStartCallIntent {
            processStartCallIntent(contact: callIntent.contacts?.first,
                                   intentType: "INStartCallIntent")
            return
        }

        if let audioIntent = intent as? INStartAudioCallIntent {
            processStartCallIntent(contact: audioIntent.contacts?.first,
                                   intentType: "INStartAudioCallIntent")
            return
        }

        NSLog("Unsupported call intent type: \(type(of: intent))")
        emitOutgoingCallFailed(to: "", displayName: nil, reason: "unsupported_intent")
    }

    @available(iOS 10.0, *)
    private func processStartCallIntent(contact: INPerson?, intentType: String) {
        guard let contact = contact else {
            NSLog("\(intentType) received without contact information")
            emitOutgoingCallFailed(to: "", displayName: nil, reason: "invalid_contact")
            return
        }

        guard let handleValue = contact.personHandle?.value, !handleValue.isEmpty else {
            let displayName = resolveDisplayName(from: contact)
            NSLog("\(intentType) contact missing handle value")
            emitOutgoingCallFailed(to: displayName ?? "", displayName: displayName, reason: "invalid_contact")
            return
        }

        let displayName = resolveDisplayName(from: contact)
        NSLog("Processing \(intentType) for handle: \(handleValue)")

        checkRecordPermission { [weak self] permissionGranted in
            guard let self = self else { return }

            guard permissionGranted else {
                NSLog("Microphone permission denied while processing \(intentType)")
                self.emitOutgoingCallFailed(to: handleValue,
                                            displayName: displayName,
                                            reason: "microphone_permission_denied")
                return
            }

            NSLog("Initiating CallKit start call action for intent target: \(handleValue)")
            self.performStartCallAction(uuid: UUID(),
                                        handle: handleValue,
                                        to: handleValue,
                                        isSystemInitiated: true,
                                        displayName: displayName,
                                        completion: { _ in })
        }
    }

    @objc func acceptCall(_ call: CAPPluginCall) {
        guard let callSid = call.getString("callSid") else {
            call.reject("callSid is required")
            return
        }

        guard let callInvite = activeCallInvites[callSid] else {
            call.reject("No pending call invite found")
            return
        }

        // Create CallKit answer action to properly handle the system UI
        let answerAction = CXAnswerCallAction(call: callInvite.uuid)
        let transaction = CXTransaction(action: answerAction)

        callKitCallController.request(transaction) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    NSLog("AnswerCallAction transaction request failed: \(error.localizedDescription)")
                    call.reject("Failed to answer call: \(error.localizedDescription)")
                } else {
                    NSLog("AnswerCallAction transaction request successful")
                    // The actual call answering will be handled in the CXProviderDelegate method
                    call.resolve(["success": true])
                }
            }
        }
    }

    @objc func rejectCall(_ call: CAPPluginCall) {
        guard let callSid = call.getString("callSid") else {
            call.reject("callSid is required")
            return
        }

        guard let callInvite = activeCallInvites[callSid] else {
            call.reject("No pending call invite found")
            return
        }

        // Create CallKit end call action to properly handle the system UI
        let endCallAction = CXEndCallAction(call: callInvite.uuid)
        let transaction = CXTransaction(action: endCallAction)

        callKitCallController.request(transaction) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    NSLog("EndCallAction transaction request failed: \(error.localizedDescription)")
                    call.reject("Failed to reject call: \(error.localizedDescription)")
                } else {
                    NSLog("EndCallAction transaction request successful")
                    // The actual call rejection will be handled in the CXProviderDelegate method
                    call.resolve(["success": true])
                }
            }
        }
    }

    @objc func endCall(_ call: CAPPluginCall) {
        let callSid = call.getString("callSid")

        if let callSid = callSid, let activeCall = activeCalls[callSid] {
            userInitiatedDisconnect = true
            performEndCallAction(uuid: activeCall.uuid!)
        } else if let activeCall = getActiveCall() {
            userInitiatedDisconnect = true
            performEndCallAction(uuid: activeCall.uuid!)
        } else {
            call.reject("No active call found")
            return
        }

        call.resolve(["success": true])
    }

    @objc func muteCall(_ call: CAPPluginCall) {
        guard let muted = call.getBool("muted") else {
            call.reject("muted parameter is required")
            return
        }

        let callSid = call.getString("callSid")
        var targetCall: Call?

        if let callSid = callSid {
            targetCall = activeCalls[callSid]
        } else {
            targetCall = getActiveCall()
        }

        guard let activeCall = targetCall else {
            call.reject("No active call found")
            return
        }

        activeCall.isMuted = muted
        call.resolve(["success": true])
    }

    @objc func setSpeaker(_ call: CAPPluginCall) {
        guard let enabled = call.getBool("enabled") else {
            call.reject("enabled parameter is required")
            return
        }

        toggleAudioRoute(toSpeaker: enabled)
        call.resolve(["success": true])
    }

    @objc func getCallStatus(_ call: CAPPluginCall) {
        let activeCall = getActiveCall()
        let hasActiveCall = activeCall != nil
        let isOnHold = activeCall?.isOnHold ?? false
        let isMuted = activeCall?.isMuted ?? false
        let callSid = activeCall?.uuid?.uuidString

        var callState = "idle"
        if let call = activeCall {
            switch call.state {
            case .connecting:
                callState = "connecting"
            case .ringing:
                callState = "ringing"
            case .connected:
                callState = "connected"
            case .reconnecting:
                callState = "reconnecting"
            case .disconnected:
                callState = "disconnected"
            @unknown default:
                callState = "unknown"
            }
        }

        // Build array of pending invites with same structure as callInviteReceived
        var pendingInvitesArray: [[String: Any]] = []
        for (callSid, callInvite) in activeCallInvites {
            let from = (callInvite.from ?? "Unknown").replacingOccurrences(of: "client:", with: "")
            let niceName = callInvite.customParameters?["CapacitorTwilioCallerName"] ?? from

            pendingInvitesArray.append([
                "callSid": callSid,
                "from": niceName,
                "to": callInvite.to,
                "customParams": callInvite.customParameters ?? [:]
            ])
        }

        call.resolve([
            "hasActiveCall": hasActiveCall,
            "isOnHold": isOnHold,
            "isMuted": isMuted,
            "callSid": callSid as Any,
            "callState": callState,
            "pendingInvites": pendingInvitesArray,
            "activeCallsCount": activeCalls.count
        ])
    }

    @objc func checkMicrophonePermission(_ call: CAPPluginCall) {
        let permissionStatus = AVAudioSession.sharedInstance().recordPermission
        let granted = permissionStatus == .granted
        call.resolve(["granted": granted])
    }

    @objc func requestMicrophonePermission(_ call: CAPPluginCall) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                call.resolve(["granted": granted])
            }
        }
    }

    // MARK: - Helper Methods

    private func getActiveCall() -> Call? {
        if let activeCall = activeCall {
            return activeCall
        } else if activeCalls.count == 1 {
            return activeCalls.first?.value
        } else {
            return nil
        }
    }

    private func checkRecordPermission(completion: @escaping (_ permissionGranted: Bool) -> Void) {
        let permissionStatus = AVAudioSession.sharedInstance().recordPermission

        switch permissionStatus {
        case .granted:
            completion(true)
        case .denied:
            completion(false)
        case .undetermined:
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                DispatchQueue.main.async {
                    completion(granted)
                }
            }
        default:
            completion(false)
        }
    }

    private func emitOutgoingCallFailed(uuid: UUID = UUID(),
                                        to: String,
                                        displayName: String?,
                                        reason: String) {
        var data: [String: Any] = [
            "callSid": uuid.uuidString,
            "to": to,
            "reason": reason
        ]

        if let displayName = displayName {
            data["displayName"] = displayName
        }

        DispatchQueue.main.async { [weak self] in
            self?.notifyListeners("outgoingCallFailed", data: data)
        }
    }

    @available(iOS 10.0, *)
    private func resolveDisplayName(from contact: INPerson) -> String? {
        if !contact.displayName.isEmpty {
            return contact.displayName
        }

        let displayName = contact.displayName

        if let nameComponents = contact.nameComponents {
            let formatter = PersonNameComponentsFormatter()
            let formattedName = formatter.string(from: nameComponents)
            if !formattedName.isEmpty {
                return formattedName
            }
        }

        return contact.personHandle?.value
    }

    private func toggleAudioRoute(toSpeaker: Bool) {
        audioDevice.block = {
            do {
                let audioSession = AVAudioSession.sharedInstance()

                // Ensure audio session is active before changing routing
                if !audioSession.isOtherAudioPlaying {
                    try audioSession.setActive(true)
                }

                if toSpeaker {
                    try audioSession.overrideOutputAudioPort(.speaker)
                } else {
                    try audioSession.overrideOutputAudioPort(.none)
                }

                NSLog("Audio route changed to: \(toSpeaker ? "speaker" : "earpiece")")
            } catch {
                NSLog("Failed to change audio route: \(error.localizedDescription)")

                // Try to recover by reconfiguring the audio session
                do {
                    let audioSession = AVAudioSession.sharedInstance()
                    try audioSession.setCategory(.playAndRecord,
                                                 mode: .voiceChat,
                                                 options: [.allowBluetooth, .allowBluetoothA2DP, .allowAirPlay])
                    try audioSession.setActive(true)

                    // Retry the audio route change
                    if toSpeaker {
                        try audioSession.overrideOutputAudioPort(.speaker)
                    } else {
                        try audioSession.overrideOutputAudioPort(.none)
                    }

                    NSLog("Audio route recovered and changed to: \(toSpeaker ? "speaker" : "earpiece")")
                } catch {
                    NSLog("Failed to recover audio route: \(error.localizedDescription)")
                }
            }
        }
        audioDevice.block()
    }

    private func registrationRequired() -> Bool {
        guard let lastBindingCreated = UserDefaults.standard.object(forKey: kCachedBindingDate) else {
            return true
        }

        let date = Date()
        var components = DateComponents()
        components.setValue(kRegistrationTTLInDays/2, for: .day)
        let expirationDate = Calendar.current.date(byAdding: components, to: lastBindingCreated as! Date)!

        if expirationDate.compare(date) == ComparisonResult.orderedDescending {
            return false
        }
        return true
    }

    // MARK: - PushKitEventDelegate

    public func credentialsUpdated(credentials: PKPushCredentials) {
        guard registrationRequired() || UserDefaults.standard.data(forKey: kCachedDeviceToken) != credentials.token else {
            return
        }

        UserDefaults.standard.set(credentials.token, forKey: kCachedDeviceToken)

        // Only perform registration if we have a valid access token
        guard let accessToken = self.accessToken, isTokenValid(accessToken) else {
            NSLog("No valid access token available. Skipping registration.")
            return
        }

        // Perform registration with new credentials
        TwilioVoiceSDK.register(accessToken: accessToken, deviceToken: credentials.token) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    NSLog("Registration failed: \(error.localizedDescription)")
                    self?.notifyListeners("registrationFailure", data: ["error": error.localizedDescription])
                } else {
                    NSLog("Successfully registered for VoIP push notifications.")
                    UserDefaults.standard.set(Date(), forKey: kCachedBindingDate)
                    self?.notifyListeners("registrationSuccess", data: nil)
                }
            }
        }
    }

    public func credentialsInvalidated() {
        guard let deviceToken = UserDefaults.standard.data(forKey: kCachedDeviceToken) else { return }

        // Only attempt unregistration if we have an access token
        if let accessToken = self.accessToken {
            TwilioVoiceSDK.unregister(accessToken: accessToken, deviceToken: deviceToken) { error in
                if let error = error {
                    NSLog("An error occurred while unregistering: \(error.localizedDescription)")
                } else {
                    NSLog("Successfully unregistered from VoIP push notifications.")
                }
            }
        }

        UserDefaults.standard.removeObject(forKey: kCachedDeviceToken)
        UserDefaults.standard.removeObject(forKey: kCachedBindingDate)
    }

    public func incomingPushReceived(payload: PKPushPayload) {
        TwilioVoiceSDK.handleNotification(payload.dictionaryPayload, delegate: self, delegateQueue: nil)
    }

    public func incomingPushReceived(payload: PKPushPayload, completion: @escaping () -> Void) {
        TwilioVoiceSDK.handleNotification(payload.dictionaryPayload, delegate: self, delegateQueue: nil)

        if let version = Float(UIDevice.current.systemVersion), version < 13.0 {
            incomingPushCompletionCallback = completion
        }
    }

    private func incomingPushHandled() {
        guard let completion = incomingPushCompletionCallback else { return }
        incomingPushCompletionCallback = nil
        completion()
    }

    // MARK: - CallKit Actions

    private func performStartCallAction(uuid: UUID,
                                        handle: String,
                                        to: String,
                                        isSystemInitiated: Bool,
                                        displayName: String? = nil,
                                        completion: @escaping (Bool) -> Void) {
        guard let provider = callKitProvider else {
            completion(false)
            return
        }

        pendingOutgoingCalls[uuid] = PendingOutgoingCall(to: to,
                                                         completion: completion,
                                                         isSystemInitiated: isSystemInitiated,
                                                         displayName: displayName)

        let callHandle = CXHandle(type: .generic, value: handle)
        let startCallAction = CXStartCallAction(call: uuid, handle: callHandle)
        let transaction = CXTransaction(action: startCallAction)

        callKitCallController.request(transaction) { [weak self] error in
            if let error = error {
                NSLog("StartCallAction transaction request failed: \(error.localizedDescription)")
                self?.pendingOutgoingCalls.removeValue(forKey: uuid)
                completion(false)

                self?.emitOutgoingCallFailed(uuid: uuid,
                                             to: to,
                                             displayName: displayName,
                                             reason: "callkit_request_failed")
                return
            }

            let callUpdate = CXCallUpdate()
            callUpdate.remoteHandle = callHandle
            callUpdate.supportsDTMF = true
            callUpdate.supportsHolding = true
            callUpdate.supportsGrouping = false
            callUpdate.supportsUngrouping = false
            callUpdate.hasVideo = false

            provider.reportCall(with: uuid, updated: callUpdate)
        }
    }

    private func reportIncomingCall(from: String, niceName: String, uuid: UUID) {
        guard let provider = callKitProvider else { return }

        let callHandle = CXHandle(type: .generic, value: from)
        let callUpdate = CXCallUpdate()

        callUpdate.remoteHandle = callHandle
        callUpdate.supportsDTMF = true
        callUpdate.supportsHolding = true
        callUpdate.supportsGrouping = false
        callUpdate.supportsUngrouping = false
        callUpdate.hasVideo = false
        callUpdate.localizedCallerName = niceName

        provider.reportNewIncomingCall(with: uuid, update: callUpdate) { error in
            if let error = error {
                NSLog("Failed to report incoming call: \(error.localizedDescription)")
            }
        }
    }

    private func performEndCallAction(uuid: UUID) {
        let endCallAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endCallAction)

        callKitCallController.request(transaction) { error in
            if let error = error {
                NSLog("EndCallAction transaction request failed: \(error.localizedDescription)")
            }
        }
    }

    private func performVoiceCall(uuid: UUID, to: String, completionHandler: @escaping (Bool) -> Void) {
        let connectOptions = ConnectOptions(accessToken: accessToken) { builder in
            builder.params = [twimlParamTo: to]
            builder.uuid = uuid
        }

        let call = TwilioVoiceSDK.connect(options: connectOptions, delegate: self)
        activeCall = call
        activeCalls[call.uuid!.uuidString] = call
        callKitCompletionCallback = completionHandler
    }

    private func performAnswerVoiceCall(uuid: UUID, completionHandler: @escaping (Bool) -> Void) {
        guard let callInvite = activeCallInvites[uuid.uuidString] else {
            completionHandler(false)
            return
        }

        let acceptOptions = AcceptOptions(callInvite: callInvite) { builder in
            builder.uuid = callInvite.uuid
        }

        let call = callInvite.accept(options: acceptOptions, delegate: self)
        activeCall = call
        activeCalls[call.uuid!.uuidString] = call
        callKitCompletionCallback = completionHandler

        activeCallInvites.removeValue(forKey: uuid.uuidString)

        guard #available(iOS 13, *) else {
            incomingPushHandled()
            return
        }
    }

    // MARK: - Ringtone

    private func playRingback() {
        guard let ringtonePath = Bundle.main.path(forResource: "ringtone", ofType: "wav") else { return }
        let ringtoneURL = URL(fileURLWithPath: ringtonePath)

        do {
            ringtonePlayer = try AVAudioPlayer(contentsOf: ringtoneURL)
            ringtonePlayer?.delegate = self
            ringtonePlayer?.numberOfLoops = -1
            ringtonePlayer?.volume = 1.0
            ringtonePlayer?.play()
        } catch {
            NSLog("Failed to initialize audio player")
        }
    }

    private func stopRingback() {
        guard let ringtonePlayer = ringtonePlayer, ringtonePlayer.isPlaying else { return }
        ringtonePlayer.stop()
    }

    private func warningString(_ warning: Call.QualityWarning) -> String {
        switch warning {
        case .highRtt: return "high-rtt"
        case .highJitter: return "high-jitter"
        case .highPacketsLostFraction: return "high-packets-lost-fraction"
        case .lowMos: return "low-mos"
        case .constantAudioInputLevel: return "constant-audio-input-level"
        default: return "unknown-warning"
        }
    }
}

// MARK: - NotificationDelegate

extension CapacitorTwilioVoicePlugin: NotificationDelegate {
    public func callInviteReceived(callInvite: CallInvite) {
        UserDefaults.standard.set(Date(), forKey: kCachedBindingDate)

        let from = (callInvite.from ?? "Unknown").replacingOccurrences(of: "client:", with: "")
        let niceName = callInvite.customParameters?["CapacitorTwilioCallerName"] ?? from
        reportIncomingCall(from: from, niceName: niceName, uuid: callInvite.uuid)
        activeCallInvites[callInvite.uuid.uuidString] = callInvite

        notifyListeners("callInviteReceived", data: [
            "callSid": callInvite.uuid.uuidString,
            "from": from,
            "to": callInvite.to,
            "customParams": callInvite.customParameters ?? [:]
        ])
    }

    public func cancelledCallInviteReceived(cancelledCallInvite: CancelledCallInvite, error: Error) {
        guard !activeCallInvites.isEmpty else { return }

        let callInvite = activeCallInvites.values.first { invite in
            invite.callSid == cancelledCallInvite.callSid
        }

        if let callInvite = callInvite {
            performEndCallAction(uuid: callInvite.uuid)
            activeCallInvites.removeValue(forKey: callInvite.uuid.uuidString)

            notifyListeners("callInviteCancelled", data: [
                "callSid": callInvite.uuid.uuidString,
                "reason": "remote_cancelled"
            ])
        }
    }
}

// MARK: - CallDelegate

extension CapacitorTwilioVoicePlugin: CallDelegate {
    public func callDidStartRinging(call: Call) {
        notifyListeners("callRinging", data: ["callSid": call.uuid!.uuidString])

        // Ensure audio is routed to earpiece during ringing (not speaker)
        toggleAudioRoute(toSpeaker: false)

        if playCustomRingback {
            playRingback()
        }
    }

    public func callDidConnect(call: Call) {
        if playCustomRingback {
            stopRingback()
        }

        if let completion = callKitCompletionCallback {
            completion(true)
        }

        // Don't force speaker on - maintain current audio routing preference
        // toggleAudioRoute(toSpeaker: true) // Removed - this was forcing speaker on
        notifyListeners("callConnected", data: ["callSid": call.uuid!.uuidString])
    }

    public func callIsReconnecting(call: Call, error: Error) {
        notifyListeners("callReconnecting", data: [
            "callSid": call.uuid!.uuidString,
            "error": error.localizedDescription
        ])
    }

    public func callDidReconnect(call: Call) {
        notifyListeners("callReconnected", data: ["callSid": call.uuid!.uuidString])
    }

    public func callDidFailToConnect(call: Call, error: Error) {
        if let completion = callKitCompletionCallback {
            completion(false)
        }

        if let provider = callKitProvider {
            provider.reportCall(with: call.uuid!, endedAt: Date(), reason: CXCallEndedReason.failed)
        }

        callDisconnected(call: call, error: error)
    }

    public func callDidDisconnect(call: Call, error: Error?) {
        if !userInitiatedDisconnect {
            var reason = CXCallEndedReason.remoteEnded
            if error != nil {
                reason = .failed
            }

            if let provider = callKitProvider {
                provider.reportCall(with: call.uuid!, endedAt: Date(), reason: reason)
            }
        }

        callDisconnected(call: call, error: error)
    }

    private func callDisconnected(call: Call, error: Error? = nil) {
        if call == activeCall {
            activeCall = nil
        }

        activeCalls.removeValue(forKey: call.uuid!.uuidString)
        userInitiatedDisconnect = false

        if playCustomRingback {
            stopRingback()
        }

        notifyListeners("callDisconnected", data: [
            "callSid": call.uuid!.uuidString,
            "error": error?.localizedDescription as Any
        ])
    }

    public func callDidReceiveQualityWarnings(call: Call, currentWarnings: Set<NSNumber>, previousWarnings: Set<NSNumber>) {
        let currentWarningStrings = currentWarnings.map { warningString(Call.QualityWarning(rawValue: $0.uintValue)!) }
        let previousWarningStrings = previousWarnings.map { warningString(Call.QualityWarning(rawValue: $0.uintValue)!) }

        notifyListeners("callQualityWarningsChanged", data: [
            "callSid": call.uuid!.uuidString,
            "currentWarnings": currentWarningStrings,
            "previousWarnings": previousWarningStrings
        ])
    }
}

// MARK: - CXProviderDelegate

extension CapacitorTwilioVoicePlugin: CXProviderDelegate {
    public func providerDidReset(_ provider: CXProvider) {
        audioDevice.isEnabled = false
    }

    public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        NSLog("CallKit activated audio session")

        // Configure the audio session for VoIP calls
        do {
            try audioSession.setCategory(.playAndRecord,
                                         mode: .voiceChat,
                                         options: [.allowBluetooth, .allowBluetoothA2DP, .allowAirPlay])
            audioDevice.isEnabled = true
            NSLog("Audio session activated and configured for call")
        } catch {
            NSLog("Failed to configure audio session during activation: \(error.localizedDescription)")
            audioDevice.isEnabled = true // Still try to enable the device
        }
    }

    public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        audioDevice.isEnabled = false
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        let uuid = action.callUUID
        let handleValue = action.handle.value

        var pendingCall = pendingOutgoingCalls[uuid]
        if pendingCall == nil {
            let fallback = PendingOutgoingCall(to: handleValue,
                                               completion: { _ in },
                                               isSystemInitiated: true,
                                               displayName: nil)
            pendingOutgoingCalls[uuid] = fallback
            pendingCall = fallback
        }

        guard let callDetails = pendingCall else {
            provider.reportCall(with: uuid, endedAt: Date(), reason: .failed)
            emitOutgoingCallFailed(uuid: uuid,
                                   to: handleValue,
                                   displayName: nil,
                                   reason: "no_call_details")
            action.fail()
            return
        }

        let to = callDetails.to
        let source = callDetails.isSystemInitiated ? "system" : "app"

        var initiatedData: [String: Any] = [
            "callSid": uuid.uuidString,
            "to": to,
            "source": source
        ]

        if let displayName = callDetails.displayName {
            initiatedData["displayName"] = displayName
        }

        notifyListeners("outgoingCallInitiated", data: initiatedData)

        guard let accessToken = accessToken, isTokenValid(accessToken) else {
            pendingOutgoingCalls.removeValue(forKey: uuid)
            provider.reportCall(with: uuid, endedAt: Date(), reason: .failed)
            callDetails.completion(false)
            emitOutgoingCallFailed(uuid: uuid,
                                   to: to,
                                   displayName: callDetails.displayName,
                                   reason: "missing_access_token")
            action.fail()
            return
        }

        provider.reportOutgoingCall(with: uuid, startedConnectingAt: Date())

        performVoiceCall(uuid: uuid, to: to) { [weak self] success in
            callDetails.completion(success)
            if !success {
                self?.emitOutgoingCallFailed(uuid: uuid,
                                             to: to,
                                             displayName: callDetails.displayName,
                                             reason: "connection_failed")
            }
        }

        pendingOutgoingCalls.removeValue(forKey: uuid)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        performAnswerVoiceCall(uuid: action.callUUID) { _ in
            // Call completion is handled in the delegate methods
        }
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        if let invite = activeCallInvites[action.callUUID.uuidString] {
            invite.reject()
            activeCallInvites.removeValue(forKey: action.callUUID.uuidString)

            notifyListeners("callInviteCancelled", data: [
                "callSid": action.callUUID.uuidString,
                "reason": "user_declined"
            ])
        } else if let call = activeCalls[action.callUUID.uuidString] {
            call.disconnect()
        }
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        if let call = activeCalls[action.callUUID.uuidString] {
            call.isOnHold = action.isOnHold
            if !call.isOnHold {
                audioDevice.isEnabled = true
                activeCall = call
            }
            action.fulfill()
        } else {
            action.fail()
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        if let call = activeCalls[action.callUUID.uuidString] {
            call.isMuted = action.isMuted
            action.fulfill()
        } else {
            action.fail()
        }
    }

    public func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) {
        NSLog("Provider timed out performing action: \(action)")
    }
}

// MARK: - AVAudioPlayerDelegate

extension CapacitorTwilioVoicePlugin: AVAudioPlayerDelegate {
    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        NSLog("Audio player finished playing successfully: \(flag)")
    }

    public func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        if let error = error {
            NSLog("Audio player decode error: \(error.localizedDescription)")
        }
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

}
