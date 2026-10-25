package si.timrekelj.thegrapevine.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.AppState


@Composable
fun OnboardingScreen(app: AppState) {
    var displayName by rememberSaveable { mutableStateOf(app.profile?.display_name.orEmpty()) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf(app.pendingInviteCode.orEmpty()) }
    var joining by rememberSaveable { mutableStateOf(app.pendingInviteCode != null) }
    var busy by remember { mutableStateOf(false) }
    var accountBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(app.pendingInviteCode) {
        app.pendingInviteCode?.let {
            inviteCode = it
            joining = true
        }
    }
    val ready = displayName.isNotBlank()

    fun run(action: suspend () -> Unit) {
        if (accountBusy) return
        scope.launch {
            busy = true
            localError = null
            try {
                app.saveDisplayName(displayName.trim())
                action()
                app.pendingInviteCode = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localError = e.message
            }
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 468.dp).fillMaxWidth().weight(1f)) {
                Text("Make yourself at home", style = LabelStyle)
                Spacer(Modifier.height(10.dp))
                Text("Your people.\nYour paper.", style = display(46))
                Spacer(Modifier.height(36.dp))
                Spacer(Modifier.weight(1f))
                PaperPreview(
                    name = displayName,
                    paper = if (joining) "" else groupName,
                    placeholder = if (joining) "Your friends’ paper" else "Your paper",
                    avatarSeed = app.session?.userId ?: "you",
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(28.dp))
                OnboardingField(displayName, { displayName = it }, "Your name", "What friends call you", capitalize = true)
                AnimatedContent(joining, label = "onboarding path") { join ->
                    Column {
                        Spacer(Modifier.height(14.dp))
                        if (join) {
                            OnboardingField(inviteCode, { inviteCode = it }, "Invite code", "From your friend’s invite")
                        } else {
                            OnboardingField(groupName, { groupName = it }, "Name your paper", "The Sunday Club", capitalize = true)
                        }
                        ErrorLine(localError)
                        app.error?.let { error ->
                            ErrorLine(error)
                            TextAction("Retry loading your paper", Modifier.padding(top = 8.dp)) { scope.launch { app.loadProfileAndGroup() } }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (join) {
                            PrimaryButton(
                                "Join your friends",
                                enabled = !busy && !accountBusy && ready && inviteCode.isNotBlank(),
                                busy = busy,
                                busyLabel = "Joining…",
                            ) { run { app.joinGroup(inviteCode.trim()) } }
                        } else {
                            PrimaryButton(
                                "Start the paper",
                                enabled = !busy && !accountBusy && ready && groupName.isNotBlank(),
                                busy = busy,
                                busyLabel = "Starting…",
                            ) { run { app.createGroup(groupName.trim()) } }
                        }
                        Spacer(Modifier.height(16.dp))
                        GhostButton(
                            if (join) "Start a new paper instead" else "Have an invite code? Join your friends",
                            enabled = !busy && !accountBusy,
                        ) { joining = !join; localError = null }
                    }
                }
                Spacer(Modifier.height(36.dp))
                AccountSettings(app, onSignedOut = {}, enabled = !busy, onBusyChange = { accountBusy = it })
            }
        }
    }
}

@Composable
private fun OnboardingField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, capitalize: Boolean = false) {
    PaperField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        label = label,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
}

/** The paper being made, updated as you type: a lead card with the paper's name and your disc. */
@Composable
private fun PaperPreview(name: String, paper: String, placeholder: String, avatarSeed: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().card(CardCorner).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                style = LabelStyle.copy(color = Press.ink3),
                modifier = Modifier.weight(1f),
            )
            Avatar(avatarSeed, 30.dp)
        }
        Text(
            paper.trim().ifEmpty { placeholder },
            style = display(32).copy(color = if (paper.isBlank()) Press.ink3 else Press.ink),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Hairline()
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PublisherTile("Your first link", Modifier.size(88.dp, 64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Share a link and it becomes the lead story.", style = display(17))
                Text("${name.trim().ifEmpty { "You" }} · 1 member", style = MetaStyle.copy(color = Press.ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
