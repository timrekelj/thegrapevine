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
        }, fallbackToSettings: true)
    }

    /// Calls `onRegistered` once, when the device has a server-assigned push
    /// subscription. The SDK holds observers weakly, so the caller must keep
    /// the returned object alive (for example in `@State`).
    func observePushRegistration(_ onRegistered: @escaping () -> Void) -> AnyObject {
        let observer = PushRegistrationObserver(onRegistered: onRegistered)
        OneSignal.User.pushSubscription.addObserver(observer)
        // The ID may already be assigned before the observer attaches.
        observer.evaluate(OneSignal.User.pushSubscription.id)
        return observer
    }
}

private final class PushRegistrationObserver: NSObject, OSPushSubscriptionObserver {
    private let onRegistered: () -> Void
    private var hasFired = false

    init(onRegistered: @escaping () -> Void) {
        self.onRegistered = onRegistered
    }

    func onPushSubscriptionDidChange(state: OSPushSubscriptionChangedState) {
        evaluate(state.current.id)
    }

    /// A real ID is non-empty and not the `local-` placeholder used before registration.
    func evaluate(_ subscriptionId: String?) {
        guard let id = subscriptionId, !id.isEmpty, !id.hasPrefix("local-") else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.hasFired else { return }
            self.hasFired = true
            self.onRegistered()
        }
    }
}
