package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.AppState

@Composable
fun LoginScreen(app: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The Grapevine", style = display(28))
        Text("A paper between friends.", style = display(17))
        Text(
            "Sign in with your email. No password, no public feed.",
            fontSize = 15.sp,
            color = Grape.ink2,
            textAlign = TextAlign.Center,
        )
        if (sent) {
            Text(
                "Check your email for a sign-in link.",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Grape.ink,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { sent = false }) { Text("Request another link", color = Grape.ink) }
        } else {
            PaperField(email, { email = it }, "Your email", keyboardType = KeyboardType.Email)
            InkButton(if (busy) "Sending…" else "Send a sign-in link", enabled = !busy && email.isNotBlank()) {
                scope.launch {
                    busy = true
                    try {
                        app.signIn(email)
                        sent = true
                    } catch (e: Exception) {
                        app.error = e.message
                    }
                    busy = false
                }
            }
        }
        app.error?.let { Text(it, fontSize = 13.sp, color = Grape.pink, textAlign = TextAlign.Center) }
    }
}
