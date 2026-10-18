package si.timrekelj.thegrapevine.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.AuthProvider
import si.timrekelj.thegrapevine.R

@Composable
fun LoginScreen(app: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            app.error = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.error = e.message
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp).padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Grape.ink, modifier = Modifier.size(24.dp))
            Text("The Grapevine", style = display(22))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (sent) "Check your inbox." else "Good stories.\nBetter company.", style = display(34))
            Text(
                if (sent) "Enter the sign-in code we sent to ${email.trim()}." else "A private paper, curated by your friends.",
                style = BodyStyle.copy(color = Grape.ink2, lineHeight = 24.sp),
            )
        }
        if (sent) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SIGN-IN CODE", style = LabelStyle)
                // Supabase codes are 6 digits by default and up to 10 if configured.
                PaperField(code, { code = it.filter(Char::isDigit).take(10) }, "Code", keyboardType = KeyboardType.Number)
            }
            InkButton(if (busy) "Signing in…" else "Sign in", enabled = !busy && code.length >= 6) {
                run { app.verifyCode(email, code) }
            }
            Column {
                TextButton(onClick = { run { app.signIn(email) } }, enabled = !busy) { Text("Send a new code", color = Grape.ink) }
                TextButton(onClick = { sent = false; code = ""; app.error = null }, enabled = !busy) {
                    Text("Use a different email", color = Grape.ink)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthProvider.entries.forEach { provider ->
                    val google = provider == AuthProvider.Google
                    OutlinedButton(
                        onClick = { run { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.startOAuth(provider)))) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (busy) 0.5f else 1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, if (google) Color(0xFF747775) else Color.Black),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (google) Color.White else Color.Black,
                            contentColor = if (google) Color(0xFF1F1F1F) else Color.White,
                        ),
                    ) {
                        if (provider == AuthProvider.Apple) {
                            // Apple's official button artwork, including its logo and wordmark.
                            Image(painterResource(R.drawable.apple_sign_in), contentDescription = provider.title,
                                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(52.dp))
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Image(painterResource(if (google) R.drawable.google_sign_in else R.drawable.x_sign_in),
                                    contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(provider.title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = Grape.rule)
                Text("or use your email", fontSize = 12.sp, color = Grape.ink2)
                HorizontalDivider(Modifier.weight(1f), color = Grape.rule)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("EMAIL ADDRESS", style = LabelStyle)
                PaperField(email, { email = it }, "you@example.com", keyboardType = KeyboardType.Email)
            }
            InkButton(if (busy) "Sending…" else "Email me a code", enabled = !busy && email.isNotBlank()) {
                run {
                    app.signIn(email)
                    sent = true
                }
            }
        }
        app.error?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.error) }
    }
}
