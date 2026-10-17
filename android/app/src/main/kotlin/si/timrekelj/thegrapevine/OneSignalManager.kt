package si.timrekelj.thegrapevine

import android.content.Context
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** The only place the app talks to the OneSignal SDK. */
object OneSignalManager {

    private var initialized = false

    fun initialize(context: Context, appId: String = Config.oneSignalAppId) {
        if (initialized) return
        if (BuildConfig.DEBUG) OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(context, appId)
        initialized = true
        watchPushRegistration()
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
    suspend fun requestPushPermission(): Boolean = OneSignal.Notifications.requestPermission(true)

    private val _integrationDialogVisible = MutableStateFlow(false)

    /** True once the device has a server-assigned push subscription, until dismissed. */
    val integrationDialogVisible: StateFlow<Boolean> = _integrationDialogVisible.asStateFlow()

    fun dismissIntegrationDialog() {
        _integrationDialogVisible.value = false
    }

    private val dialogShown = AtomicBoolean(false)

    // OneSignal holds observers weakly, so this keeps it alive for the process.
    private var pushObserver: IPushSubscriptionObserver? = null

    private fun watchPushRegistration() {
        val observer = object : IPushSubscriptionObserver {
            override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                evaluate(state.current.id)
            }
        }
        pushObserver = observer
        OneSignal.User.pushSubscription.addObserver(observer)
        // The ID may already be assigned before the observer attaches.
        evaluate(OneSignal.User.pushSubscription.id)
    }

    /** A real ID is non-empty and not the `local-` placeholder used before registration. */
    private fun evaluate(subscriptionId: String?) {
        if (subscriptionId.isNullOrEmpty() || subscriptionId.startsWith("local-")) return
        if (dialogShown.compareAndSet(false, true)) _integrationDialogVisible.value = true
    }
}
