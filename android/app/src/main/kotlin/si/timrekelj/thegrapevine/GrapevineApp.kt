package si.timrekelj.thegrapevine

import android.app.Application

/** Process-wide singletons, built once and shared by the ViewModels. */
class GrapevineApp : Application() {
    val api: Api by lazy { Api() }
    val sessionStore: SessionStore by lazy { SessionStore(this) }
}
