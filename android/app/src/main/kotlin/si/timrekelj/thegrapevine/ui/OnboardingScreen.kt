package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.AppState

@Composable
fun OnboardingScreen(app: AppState) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf(app.pendingInviteCode.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(app.pendingInviteCode) { app.pendingInviteCode?.let { inviteCode = it } }

    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            localError = null
            try {
                if (displayName.isNotBlank()) app.saveDisplayName(displayName)
                action()
                app.pendingInviteCode = null
            } catch (e: Exception) {
                localError = e.message
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to the paper", style = display(24))
        PaperField(displayName, { displayName = it }, "Your name")
        Text("Start a paper", style = display(17))
        PaperField(groupName, { groupName = it }, "Name your paper")
        InkButton(if (busy) "Starting…" else "Start the paper", enabled = !busy && groupName.isNotBlank()) {
            run { app.createGroup(groupName.trim()) }
        }
        Text("or join a friend's paper", fontSize = 13.sp, color = Grape.ink2)
        PaperField(inviteCode, { inviteCode = it }, "Invite code")
        InkButton("Join with a code", enabled = !busy && inviteCode.isNotBlank()) {
            run { app.joinGroup(inviteCode) }
        }
        localError?.let { Text(it, fontSize = 13.sp, color = Grape.pink) }
        app.error?.let { error ->
            Text(error, fontSize = 15.sp, color = Grape.ink)
            TextButton(onClick = { scope.launch { app.loadProfileAndGroup() } }) {
                Text("Retry loading your paper", color = Grape.ink)
            }
        }
    }
}
