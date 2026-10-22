package si.timrekelj.thegrapevine.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
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

private val Field = RoundedCornerShape(14.dp)
private val ErrorRed = Color(0xFF9A3149)

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
                Text("Your people.\nYour paper.", style = display(40))
                Spacer(Modifier.height(36.dp))
                Spacer(Modifier.weight(1f))
                PaperPreview(
                    name = displayName,
                    paper = if (joining) "" else groupName,
                    placeholder = if (joining) "Your friends’ paper" else "Your paper",
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
                                Text("Retry loading your paper", color = Grape.ink)
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
                            border = BorderStroke(1.dp, Grape.rule),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text(
                                if (join) "Start a new paper instead" else "Have an invite code? Join your friends",
                                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Grape.ink,
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
        placeholder = { Text(placeholder, color = Grape.ink2.copy(alpha = 0.6f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None, autoCorrectEnabled = false),
        shape = Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Grape.raised,
            unfocusedContainerColor = Grape.raised,
            focusedBorderColor = Grape.ink,
            unfocusedBorderColor = Grape.rule,
            cursorColor = Grape.ink,
            focusedTextColor = Grape.ink,
            unfocusedTextColor = Grape.ink,
        ),
        modifier = Modifier.fillMaxWidth(),
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

/** The paper being made, updated as you type: masthead, monogram and a first-edition stamp. */
@Composable
private fun PaperPreview(name: String, paper: String, placeholder: String, modifier: Modifier = Modifier) {
    val sheet = RoundedCornerShape(4.dp)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(modifier.rotate(-1f)) {
        Column(
            Modifier.fillMaxWidth().shadow(12.dp, sheet, ambientColor = Grape.onPink, spotColor = Grape.onPink)
                .background(Grape.raised, sheet).border(1.dp, Grape.rule, sheet)
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
                    style = LabelStyle.copy(fontSize = 10.sp),
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(26.dp).background(Grape.ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initial, style = display(13).copy(color = Grape.paper))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                paper.trim().ifEmpty { placeholder },
                style = display(30).copy(color = if (paper.isBlank()) Grape.ink2 else Grape.ink),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Grape.ink, thickness = 1.dp)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = Grape.ink, thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp, 72.dp).background(Grape.paper, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Halftone(Modifier.matchParentSize())
                    Text(
                        "Your first link", style = display(12),
                        modifier = Modifier.background(Grape.raised).padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Share a link and it becomes the lead story.", style = display(17))
                    Text("${name.trim().ifEmpty { "You" }} · 1 member", style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-12).dp).rotate(4f)) {
            Stamp("First edition")
        }
    }
}

/** A flat 30% screen, the plate printed for a story with no picture. */
@Composable
private fun Halftone(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 4.5.dp.toPx()
        val radius = 1.1.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            var y = step / 2
            while (y < size.height) {
                drawCircle(Grape.ink, radius, Offset(x, y))
                y += step
            }
            x += step
        }
    }
}
