package si.timrekelj.thegrapevine.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.AppState

private val Field = Corner

@Composable
fun OnboardingScreen(app: AppState) {
    var displayName by rememberSaveable { mutableStateOf(app.profile?.display_name.orEmpty()) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf(app.pendingInviteCode.orEmpty()) }
    var joining by rememberSaveable { mutableStateOf(app.pendingInviteCode != null) }
    var busy by remember { mutableStateOf(false) }
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
                Text("MAKE YOURSELF AT HOME", style = LabelStyle)
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
                Text("YOUR NAME", style = LabelStyle)
                Spacer(Modifier.height(8.dp))
                OnboardingField(displayName, { displayName = it }, "What friends call you", capitalize = true)
                AnimatedContent(joining, label = "onboarding path") { join ->
                    Column {
                        Spacer(Modifier.height(18.dp))
                        Text(if (join) "INVITE CODE" else "NAME YOUR PAPER", style = LabelStyle)
                        Spacer(Modifier.height(8.dp))
                        if (join) {
                            OnboardingField(inviteCode, { inviteCode = it }, "From your friend’s invite")
                        } else {
                            OnboardingField(groupName, { groupName = it }, "The Sunday Club", capitalize = true)
                        }
                        ErrorLine(localError)
                        app.error?.let { error ->
                            ErrorLine(error)
                            TextButton(onClick = { scope.launch { app.loadProfileAndGroup() } }) {
                                Text("Retry loading your paper", style = MetaStyle.copy(color = Press.ink))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (join) {
                            PrimaryButton(
                                if (busy) "Joining…" else "Join your friends",
                                enabled = !busy && ready && inviteCode.isNotBlank(),
                            ) { run { app.joinGroup(inviteCode.trim()) } }
                        } else {
                            PrimaryButton(
                                if (busy) "Starting…" else "Start the paper",
                                enabled = !busy && ready && groupName.isNotBlank(),
                            ) { run { app.createGroup(groupName.trim()) } }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { joining = !join; localError = null },
                            enabled = !busy,
                            shape = Field,
                            border = BorderStroke(1.dp, Press.rule),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text(
                                if (join) "Start a new paper instead" else "Have an invite code? Join your friends",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Press.ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingField(value: String, onChange: (String) -> Unit, placeholder: String, capitalize: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, style = BodyStyle.copy(color = Press.ink3)) },
        textStyle = BodyStyle,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None, autoCorrectEnabled = false),
        shape = Field,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorLine(error: String?) {
    if (error == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = Press.danger, modifier = Modifier.size(18.dp))
        Text(error, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Press.danger)
    }
}

/** The paper being made, updated as you type: its nameplate in blackletter, a first-edition stamp. */
@Composable
private fun PaperPreview(name: String, paper: String, placeholder: String, avatarSeed: String, modifier: Modifier = Modifier) {
    val sheet = RoundedCornerShape(2.dp)
    Box(modifier.rotate(-1f)) {
        Column(
            Modifier.fillMaxWidth().shadow(14.dp, sheet, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Press.raised, sheet).border(1.dp, Press.rule, sheet)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
                    style = LabelStyle.copy(fontSize = 9.sp, color = Press.ink2),
                    modifier = Modifier.weight(1f),
                )
                Avatar(avatarSeed, 30.dp)
            }
            Text(
                paper.trim().ifEmpty { placeholder },
                style = nameplate(38).copy(color = if (paper.isBlank()) Press.ink3 else Press.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            )
            DoubleRule()
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TypePlate("Your first link", Modifier.size(96.dp, 72.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Share a link and it becomes the lead story.", style = display(18))
                    Text("${name.trim().ifEmpty { "You" }} · 1 member", style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-12).dp).rotate(4f)) {
            Stamp("First edition")
        }
    }
}
