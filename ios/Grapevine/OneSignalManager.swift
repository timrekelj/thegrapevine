import Foundation
import OneSignalFramework

/// The only place the app talks to the OneSignal SDK. Stateless, so safe to share across threads.
final class OneSignalManager: NSObject, OSNotificationClickListener, @unchecked Sendable {
    static let shared = OneSignalManager()

    private override init() { super.init() }

    func initialize() {
        #if DEBUG
        OneSignal.Debug.setLogLevel(.LL_VERBOSE)
        #endif
        // The SwiftUI lifecycle has no launch options.
        OneSignal.initialize(Config.oneSignalAppId, withLaunchOptions: nil)
        OneSignal.Notifications.addClickListener(self)
    }

    func onClick(event: OSNotificationClickEvent) {
        Telemetry.capture("notification_opened")
    }

    // MARK: - Identity

    func login(externalId: String) {
        OneSignal.login(externalId)
    }

    func logout() {
        OneSignal.logout()
    }

    // MARK: - Push

    func requestPushPermission() {
        OneSignal.Notifications.requestPermission({ _ in }, fallbackToSettings: false)
    }
}
