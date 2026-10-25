package si.timrekelj.thegrapevine.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.AuthProvider
import si.timrekelj.thegrapevine.Config
import si.timrekelj.thegrapevine.R

/** Must match `otp_length` in supabase/config.toml and the hosted project. */
const val SIGN_IN_CODE_LENGTH = 6
private const val RESEND_SECONDS = 30

@Composable
fun LoginScreen(app: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    /** Which action is running, so only its button shows the busy dots. */
    var working by remember { mutableStateOf<String?>(null) }
    var resendLeft by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun run(what: String? = null, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            working = what
            app.error = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.error = e.message
            }
            busy = false
            working = null
        }
    }

    fun sendCode() {
        if (busy || email.isBlank()) return
        run("code") {
            app.signIn(email)
            code = ""
            resendLeft = RESEND_SECONDS
            sent = true
        }
    }

    fun verifyCode() = run("verify") {
        try {
            app.verifyCode(email, code)
        } catch (e: Exception) {
            // Clear the cells so the next attempt can be typed straight in.
            if (e !is CancellationException) code = ""
            throw e
        }
    }

    fun changeEmail() {
        sent = false
        code = ""
        app.error = null
    }

    BackHandler(enabled = sent && !busy) { changeEmail() }
    LaunchedEffect(resendLeft) {
        if (resendLeft > 0) {
            delay(1000)
            resendLeft -= 1
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 468.dp).fillMaxWidth().weight(1f)) {
                Masthead(onBack = if (sent) ({ changeEmail() }) else null)
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (sent) "Check your inbox." else "Good stories.\nBetter company.", style = display(44))
                    if (sent) {
                        Text(
                            buildAnnotatedString {
                                append("A $SIGN_IN_CODE_LENGTH-digit code is on its way to ")
                                withStyle(SpanStyle(color = Press.ink, fontWeight = FontWeight.SemiBold)) { append(email.trim()) }
                            },
                            style = BodyStyle.copy(color = Press.ink2),
                        )
                    } else {
                        Text("A private paper, curated by your friends.", style = BodyStyle.copy(color = Press.ink2))
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (sent) {
                    CodeCells(code, invalid = app.error != null, onChange = {
                        code = it.filter(Char::isDigit).take(SIGN_IN_CODE_LENGTH)
                        if (code.length == SIGN_IN_CODE_LENGTH && !busy) verifyCode()
                    })
                    ErrorLine(app.error)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Sign in", enabled = !busy && code.length == SIGN_IN_CODE_LENGTH, busy = working == "verify", busyLabel = "Signing in…") { verifyCode() }
                    Spacer(Modifier.height(16.dp))
                    QuietButton(
                        if (resendLeft > 0) "Send a new code in 0:%02d".format(resendLeft) else "Send a new code",
                        enabled = !busy && resendLeft == 0,
                        busy = working == "resend",
                    ) {
                        run("resend") {
                            app.signIn(email)
                            code = ""
                            resendLeft = RESEND_SECONDS
                        }
                    }
                } else {
                    PaperField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "you@example.com",
                        label = "Email address",
                        isError = app.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCode() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                    )
                    ErrorLine(app.error)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Email me a code", enabled = !busy && email.isNotBlank(), busy = working == "code", busyLabel = "Sending…") { sendCode() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 24.dp),
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = Press.rule)
                        Text("or", style = MetaStyle.copy(fontSize = 15.sp, color = Press.ink3))
                        HorizontalDivider(Modifier.weight(1f), color = Press.rule)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthProvider.entries.forEach { provider ->
                            ProviderButton(provider, enabled = !busy, busy = working == provider.name) {
                                run(provider.name) { context.startActivity(Intent(Intent.ACTION_VIEW, app.startOAuth(provider).toUri())) }
                            }
                        }
                    }
                }
                Footer(Modifier.padding(top = 32.dp))
            }
        }
    }
}

/** The paper's name beside the vine mark, with a way back while a code is pending. */
@Composable
private fun Masthead(onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Use a different email", tint = Press.ink)
            }
        } else {
            // The app icon's bunch, in ink so it holds in dark mode. The art carries the adaptive-icon
            // margin, so it is drawn larger than the box that lays it out.
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_vine_monochrome), contentDescription = null, tint = Press.ink, modifier = Modifier.requiredSize(56.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text("The Grapevine", style = display(24), maxLines = 1)
    }
}

/** One real text field (so autofill, paste and TalkBack just work), drawn as cells. */
@Composable
private fun CodeCells(code: String, invalid: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = code,
        onValueChange = onChange,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .semantics { contentType = ContentType.SmsOtpCode; contentDescription = "Sign-in code" },
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { innerTextField ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(SIGN_IN_CODE_LENGTH) { index ->
                        val active = focused && index == minOf(code.length, SIGN_IN_CODE_LENGTH - 1)
                        Box(
                            Modifier.weight(1f).height(62.dp).background(Press.raised2, FieldCorner)
                                .border(if (active) 1.5.dp else 1.dp, if (invalid) Press.danger else if (active) Press.ink else Press.rule, FieldCorner),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(code.getOrNull(index)?.toString() ?: "", style = BodyStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        }
                    }
                }
                // The real (invisible) text field sits over the cells and takes the input.
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { innerTextField() }
            }
        },
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean, busy: Boolean = false, busyLabel: String? = null, onClick: () -> Unit) {
    InkButton(text, enabled = enabled, height = 52.dp, busy = busy, busyLabel = busyLabel, onClick = onClick)
}

@Composable
private fun QuietButton(text: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    GhostButton(text, enabled = enabled, busy = busy, busyLabel = "Sending a new code…", onClick = onClick)
}

/** A sign-in provider on a raised capsule; while its browser is being opened, busy dots replace its name. */
@Composable
private fun ProviderButton(provider: AuthProvider, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .pressable(enabled = enabled, role = Role.Button, onClickLabel = provider.title, onClick = onClick)
            .heightIn(min = 52.dp).alpha(if (enabled || busy) 1f else 0.5f)
            .clip(CircleShape).background(Press.raised).border(1.dp, Press.rule, CircleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) InkDots(contentDescription = "Opening ${provider.title}")
        Row(Modifier.alpha(if (busy) 0f else 1f), verticalAlignment = Alignment.CenterVertically) { ProviderLabel(provider) }
    }
}

@Composable
private fun ProviderLabel(provider: AuthProvider) {
        when (provider) {
            AuthProvider.Google -> Image(painterResource(R.drawable.google_sign_in), contentDescription = null, modifier = Modifier.size(20.dp))
            AuthProvider.Apple -> Icon(painterResource(R.drawable.apple_logo), contentDescription = null, tint = Press.ink, modifier = Modifier.size(20.dp))
            AuthProvider.X -> Icon(painterResource(R.drawable.x_sign_in), contentDescription = null, tint = Press.ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(provider.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Press.ink)
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val links = TextLinkStyles(SpanStyle(color = Press.ink, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("By continuing, you agree to the ")
            withLink(LinkAnnotation.Url("${Config.webURL}/terms", links)) { append("Terms") }
            append(" and ")
            withLink(LinkAnnotation.Url("${Config.webURL}/privacy", links)) { append("Privacy Policy") }
            append(".")
        },
        style = MetaStyle.copy(textAlign = TextAlign.Center),
        modifier = modifier.fillMaxWidth(),
    )
}
