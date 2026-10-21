package si.timrekelj.thegrapevine.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
private val Field = RoundedCornerShape(14.dp)
private val ErrorRed = Color(0xFF9A3149)

@Composable
fun LoginScreen(app: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resendLeft by rememberSaveable { mutableIntStateOf(0) }
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

    fun sendCode() {
        if (busy || email.isBlank()) return
        run {
            app.signIn(email)
            code = ""
            resendLeft = RESEND_SECONDS
            sent = true
        }
    }

    fun verifyCode() = run {
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
                Nameplate(onBack = if (sent) ({ changeEmail() }) else null)
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (sent) "Check your inbox." else "Good stories.\nBetter company.", style = display(40))
                    if (sent) {
                        Text(
                            buildAnnotatedString {
                                append("We sent a $SIGN_IN_CODE_LENGTH-digit code to ")
                                withStyle(SpanStyle(color = Grape.ink, fontWeight = FontWeight.SemiBold)) { append(email.trim()) }
                            },
                            style = BodyStyle.copy(color = Grape.ink2),
                        )
                    } else {
                        Text("A private paper, curated by your friends.", style = BodyStyle.copy(color = Grape.ink2))
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
                    PrimaryButton(if (busy) "Signing in…" else "Sign in", enabled = !busy && code.length == SIGN_IN_CODE_LENGTH) { verifyCode() }
                    Spacer(Modifier.height(16.dp))
                    QuietButton(
                        if (resendLeft > 0) "Send a new code in 0:%02d".format(resendLeft) else "Send a new code",
                        enabled = !busy && resendLeft == 0,
                    ) {
                        run {
                            app.signIn(email)
                            code = ""
                            resendLeft = RESEND_SECONDS
                        }
                    }
                } else {
                    Text("EMAIL ADDRESS", style = LabelStyle)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("you@example.com", color = Grape.ink2) },
                        singleLine = true,
                        isError = app.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCode() }),
                        shape = Field,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Grape.raised,
                            unfocusedContainerColor = Grape.raised,
                            errorContainerColor = Grape.raised,
                            focusedBorderColor = Grape.ink,
                            unfocusedBorderColor = Grape.rule,
                            errorBorderColor = ErrorRed,
                            cursorColor = Grape.ink,
                            focusedTextColor = Grape.ink,
                            unfocusedTextColor = Grape.ink,
                            errorTextColor = Grape.ink,
                        ),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                    )
                    ErrorLine(app.error)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (busy) "Sending…" else "Email me a code", enabled = !busy && email.isNotBlank()) { sendCode() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 24.dp),
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = Grape.rule)
                        Text("or", style = MetaStyle)
                        HorizontalDivider(Modifier.weight(1f), color = Grape.rule)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthProvider.entries.forEach { provider ->
                            ProviderButton(provider, enabled = !busy) {
                                run { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.startOAuth(provider)))) }
                            }
                        }
                    }
                }
                Footer(Modifier.padding(top = 32.dp))
            }
        }
    }
}

@Composable
private fun Nameplate(onBack: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("The Grapevine", style = display(28))
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Grape.ink, thickness = 1.dp)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = Grape.ink, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM"))} · Private edition".uppercase(),
                style = LabelStyle,
            )
        }
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp).size(40.dp).align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Use a different email", tint = Grape.ink)
            }
        }
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
                            Modifier.weight(1f).height(58.dp).background(Grape.raised, Field)
                                .border(if (active) 1.5.dp else 1.dp, if (invalid) ErrorRed else if (active) Grape.ink else Grape.rule, Field),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(code.getOrNull(index)?.toString() ?: "", style = display(28))
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
private fun ErrorLine(error: String?) {
    if (error == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
        Text(error, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = ErrorRed)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    // Dim only the fill when disabled, so the label stays readable.
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).background(Grape.ink.copy(alpha = if (enabled) 1f else 0.45f), Field),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled, shape = Field, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Grape.paper)
        }
    }
}

@Composable
private fun QuietButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Field,
        border = BorderStroke(1.dp, if (enabled) Grape.rule else Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (enabled) Grape.ink else Grape.ink2)
    }
}

@Composable
private fun ProviderButton(provider: AuthProvider, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).alpha(if (enabled) 1f else 0.5f),
        shape = Field,
        contentPadding = PaddingValues(horizontal = 16.dp),
        border = BorderStroke(1.dp, Grape.rule),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Grape.raised, contentColor = Grape.ink),
    ) {
        when (provider) {
            AuthProvider.Google -> Image(painterResource(R.drawable.google_sign_in), contentDescription = null, modifier = Modifier.size(20.dp))
            AuthProvider.Apple -> Icon(painterResource(R.drawable.apple_logo), contentDescription = null, tint = Grape.ink, modifier = Modifier.size(20.dp))
            AuthProvider.X -> Icon(painterResource(R.drawable.x_sign_in), contentDescription = null, tint = Grape.ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(provider.title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val links = TextLinkStyles(SpanStyle(color = Grape.ink, textDecoration = TextDecoration.Underline))
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
