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
import si.timrekelj.thegrapevine.ui.RootScreen

class MainActivity : ComponentActivity() {

    private val grapevine get() = application as GrapevineApp

    private val appState: AppState by viewModels {
        viewModelFactory { initializer { AppState(grapevine.api, grapevine.sessionStore) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { uri ->
            if (savedInstanceState == null || OAuth.isCallback(uri)) appState.handleDeepLink(uri)
        }
        setContent {
            GrapevineTheme {
                CompositionLocalProvider(LocalApi provides grapevine.api) {
                    RootScreen(appState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { appState.handleDeepLink(it.toString()) }
    }
}
