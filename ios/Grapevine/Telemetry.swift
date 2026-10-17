import Foundation
import PostHog

enum Telemetry {
    static func initialize() {
        // Debug builds stay out of the production project.
        #if !DEBUG
        let config = PostHogConfig(projectToken: "phc_kXSTZhnAXUB44jnt3FK8k88YnoLtXuVVZsumUBptu6j9", host: "https://eu.i.posthog.com")
        config.captureScreenViews = false
        config.captureElementInteractions = false
        config.capturePushNotificationSubscriptions = false
        config.capturePushNotificationOpened = false
        config.sessionReplay = false
        config.surveys = false
        config.disableGeoIp = true
        config.errorTrackingConfig.autoCapture = true
        config.setBeforeSend { event in
            event.properties["platform"] = "ios"
            event.properties["environment"] = "production"
            if var exceptions = event.properties["$exception_list"] as? [[String: Any]] {
                for index in exceptions.indices { exceptions[index]["value"] = "Redacted" }
                event.properties["$exception_list"] = exceptions
            }
            event.properties.removeValue(forKey: "$exception_message")
            return event
        }
        PostHogSDK.shared.setup(config)
        #endif
    }

    static func identify(_ id: UUID) { PostHogSDK.shared.identify(id.uuidString.lowercased()) }
    static func reset() { PostHogSDK.shared.reset() }
    static func capture(_ event: String, _ properties: [String: Any] = [:]) {
        PostHogSDK.shared.capture(event, properties: properties)
    }
    static func correctionsEnabled() -> Bool {
        PostHogSDK.shared.getFeatureFlag("story-corrections-enabled") as? Bool ?? true
    }
    static func httpError(_ status: Int) {
        PostHogSDK.shared.captureException(NSError(domain: "SupabaseHTTP", code: status), properties: ["status": status])
    }
}
