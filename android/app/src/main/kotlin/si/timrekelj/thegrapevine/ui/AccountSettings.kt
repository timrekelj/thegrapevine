package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.ApiError
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.emailChangeNotice
import si.timrekelj.thegrapevine.isSignInEmail

/**
 * Email change, sign-out, and permanent account deletion.
 * Shown on the profile tab and on onboarding, so a signed-in person can leave before they have a paper.
 */
@Composable
fun AccountSettings(
    app: AppState,
    onSignedOut: () -> Unit,
    enabled: Boolean = true,
    onBusyChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(app.session?.email.orEmpty()) }
    var edited by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var emailNotice by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val currentEmail = app.session?.email.orEmpty()
    val trimmed = email.trim()
    val emailChanged = trimmed.isNotEmpty() && !trimmed.equals(currentEmail, ignoreCase = true)
    val canSend = enabled && !sending && !deleting && emailChanged && isSignInEmail(trimmed)

    LaunchedEffect(currentEmail) {
        if (!edited) email = currentEmail
    }
    LaunchedEffect(sending, deleting) { onBusyChange(sending || deleting) }
    DisposableEffect(Unit) {
        onDispose { onBusyChange(false) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHead("Account")
        Text(
            "Your sign-in email. A change is confirmed from both your current address and the new one before it takes effect.",
            style = MetaStyle.copy(lineHeight = 18.sp),
        )
        PaperField(
            email,
            {
                email = it
                edited = true
                emailNotice = null
                emailError = null
            },
            "Email",
            enabled = enabled && !sending && !deleting,
            keyboardType = KeyboardType.Email,
            isError = emailError != null,
        )
        if (edited && trimmed.isNotEmpty() && !isSignInEmail(trimmed)) {
            Text("Enter a valid email address.", color = Press.danger, style = MetaStyle)
        }
        InkButton(if (sending) "Sending…" else "Send confirmation", enabled = canSend) {
            sending = true
            emailError = null
            emailNotice = null
            scope.launch {
                try {
                    val change = app.changeEmail(email)
                    emailNotice = emailChangeNotice(currentEmail, change)
                    email = app.session?.email.orEmpty()
                    edited = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emailError = if (e is ApiError) e.message else "Could not change your email. Please try again."
                }
                sending = false
            }
        }
        emailNotice?.let { Text(it, style = MetaStyle.copy(lineHeight = 18.sp)) }
        emailError?.let { Text(it, color = Press.danger, fontSize = 13.sp) }
        TextButton(
            onClick = {
                onSignedOut()
                app.signOut()
            },
            enabled = enabled && !sending && !deleting,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Sign out", style = MetaStyle.copy(color = Press.danger, fontWeight = FontWeight.SemiBold))
        }
        TextButton(
            onClick = {
                deleteError = null
                confirmDelete = true
            },
            enabled = enabled && !sending && !deleting,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Delete account", style = MetaStyle.copy(color = Press.danger, fontWeight = FontWeight.SemiBold))
        }
    }

    if (confirmDelete) {
        Dialog(
            onDismissRequest = {
                if (!deleting) {
                    confirmDelete = false
                    deleteError = null
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !deleting,
                dismissOnClickOutside = !deleting,
            ),
        ) {
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .background(Press.paper).border(1.dp, Press.ink).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Delete your account?", style = display(26))
                Text(
                    "This permanently deletes your account and signs you out of The Grapevine. Your name comes off the paper. Stories and links you shared stay, without your name. This cannot be undone.",
                    style = BodyStyle.copy(color = Press.ink2),
                )
                deleteError?.let { Text(it, color = Press.danger, fontSize = 13.sp) }
                DangerButton(if (deleting) "Deleting…" else "Delete account", enabled = !deleting) {
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            app.deleteAccount()
                            withContext(NonCancellable) {
                                onSignedOut()
                                app.signOut()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            deleteError = if (e is ApiError) e.message else "Could not delete your account. Please try again."
                            deleting = false
                        }
                    }
                }
                GhostButton("Keep my account", enabled = !deleting) {
                    confirmDelete = false
                    deleteError = null
                }
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(Corner)
            .background(if (enabled) Press.danger else Press.danger.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Press.onRed, maxLines = 1)
    }
}
