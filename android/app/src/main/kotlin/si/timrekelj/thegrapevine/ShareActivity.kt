package si.timrekelj.thegrapevine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import si.timrekelj.thegrapevine.ui.GrapevineTheme
import si.timrekelj.thegrapevine.ui.LocalApi
import si.timrekelj.thegrapevine.ui.ShareSheet

/**
 * "The Grapevine" in the system share sheet: shows the share sheet over the
 * app the link came from, with the link filled in, and posts it to the paper.
 */
class ShareActivity : ComponentActivity() {

    private val grapevine get() = application as GrapevineApp

    private val appState: AppState by viewModels {
        viewModelFactory { initializer { AppState(grapevine.api, grapevine.sessionStore) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = SharedLink.extract(intent?.getStringExtra(Intent.EXTRA_TEXT)).orEmpty()
        setContent {
            GrapevineTheme {
                CompositionLocalProvider(LocalApi provides grapevine.api) {
                    ShareSheet(appState, onDismiss = ::finish, initialUrl = url, onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    })
                }
            }
        }
    }
}
