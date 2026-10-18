package si.timrekelj.thegrapevine

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

object Telemetry {
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG) return
        val config = PostHogAndroidConfig(
            apiKey = "phc_kXSTZhnAXUB44jnt3FK8k88YnoLtXuVVZsumUBptu6j9",
            host = "https://eu.i.posthog.com",
        ).apply {
            captureScreenViews = false
            captureDeepLinks = false
            capturePushNotificationSubscriptions = false
            capturePushNotificationOpened = false
            sessionReplay = false
            surveys = false
            errorTrackingConfig.autoCapture = true
            addBeforeSend { event ->
                event.properties?.apply {
                    put("platform", "android")
                    put("environment", "production")
                    remove("\$exception_message")
                    val exceptions = get("\$exception_list") as? List<*>
                    if (exceptions != null) put("\$exception_list", exceptions.map {
                        (it as? Map<*, *>)?.toMutableMap()?.apply { put("value", "Redacted") } ?: it
                    })
                }
                event
            }
        }
        PostHogAndroid.setup(context, config)
    }

    fun identify(id: String) = PostHog.identify(id.lowercase())
    fun reset() = PostHog.reset()
    fun capture(event: String, properties: Map<String, Any> = emptyMap()) = PostHog.capture(event, properties = properties)
    fun correctionsEnabled(): Boolean = PostHog.getFeatureFlag("story-corrections-enabled") as? Boolean ?: true
    fun httpError(status: Int) = PostHog.captureException(IllegalStateException("Supabase HTTP $status"), properties = mapOf("status" to status))
}
