package si.timrekelj.thegrapevine

import android.app.Application
import okhttp3.Cache
import java.io.File

/** Process-wide singletons, built once and shared by the ViewModels. */
class GrapevineApp : Application() {
    val api: Api by lazy {
        val http = Api.defaultHttpClient()
        // One connection pool for both; only pictures get the disk cache.
        Api(http, pictures = http.newBuilder().cache(Cache(File(cacheDir, "pictures"), 50L * 1024 * 1024)).build())
    }
    val sessionStore: SessionStore by lazy { SessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        Telemetry.initialize(this)
        OneSignalManager.initialize(this)
    }
}
