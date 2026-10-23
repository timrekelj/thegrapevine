package si.timrekelj.thegrapevine

import android.content.Context
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationClickEvent

/** The only place the app talks to the OneSignal SDK. */
object OneSignalManager : INotificationClickListener {

    private var initialized = false

    fun initialize(context: Context, appId: String = Config.oneSignalAppId) {
        if (initialized) return
        if (BuildConfig.DEBUG) OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(context, appId)
        initialized = true
        OneSignal.Notifications.addClickListener(this)
    }

    override fun onClick(event: INotificationClickEvent) {
        Telemetry.capture("notification_opened")
    }

    // ------------------------------------------------------------- identity

    fun login(externalId: String) = OneSignal.login(externalId)

    fun logout() = OneSignal.logout()

    // ----------------------------------------------------------------- push

    /** Asks for notification permission; true when granted. */
    suspend fun requestPushPermission(): Boolean = OneSignal.Notifications.requestPermission(false)
}
