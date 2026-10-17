import Foundation
import OneSignalFramework

/// The only place the app talks to the OneSignal SDK.
final class OneSignalManager {
    static let shared = OneSignalManager()

    private init() {}

    func initialize(appId: String = Config.oneSignalAppId) {
        #if DEBUG
        OneSignal.Debug.setLogLevel(.LL_VERBOSE)
        #endif
        // The SwiftUI lifecycle has no launch options.
        OneSignal.initialize(appId, withLaunchOptions: nil)
    }

    // MARK: - Identity

    func login(externalId: String) {
        OneSignal.login(externalId)
    }

    func logout() {
        OneSignal.logout()
    }

    // MARK: - Subscriptions and tags

    func setEmail(_ email: String) {
        OneSignal.User.addEmail(email)
    }

    func setSmsNumber(_ number: String) {
        OneSignal.User.addSms(number)
    }

    func setTag(key: String, value: String) {
        OneSignal.User.addTag(key: key, value: value)
    }

    func setLogLevel(_ level: ONE_S_LOG_LEVEL) {
        OneSignal.Debug.setLogLevel(level)
    }

    // MARK: - Push

    func requestPushPermission() {
        OneSignal.Notifications.requestPermission({ accepted in
            print("User accepted notifications: \(accepted)")
        }, fallbackToSettings: false)
    }
}
