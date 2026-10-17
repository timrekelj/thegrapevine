package si.timrekelj.thegrapevine

import android.content.Context
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

/** The only place the app talks to the OneSignal SDK. */
object OneSignalManager {

    private var initialized = false

    fun initialize(context: Context, appId: String = Config.oneSignalAppId) {
        if (initialized) return
        if (BuildConfig.DEBUG) OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(context, appId)
        initialized = true
    }

    // ------------------------------------------------------------- identity

    fun login(externalId: String) = OneSignal.login(externalId)

    fun logout() = OneSignal.logout()

    // -------------------------------------------------- subscriptions and tags

    fun setEmail(email: String) = OneSignal.User.addEmail(email)

    fun setSmsNumber(number: String) = OneSignal.User.addSms(number)

    fun setTag(key: String, value: String) = OneSignal.User.addTag(key, value)

    fun setLogLevel(level: LogLevel) {
        OneSignal.Debug.logLevel = level
    }

    // ----------------------------------------------------------------- push

    /** Asks for notification permission; true when granted. */
    suspend fun requestPushPermission(): Boolean = OneSignal.Notifications.requestPermission(false)
}
