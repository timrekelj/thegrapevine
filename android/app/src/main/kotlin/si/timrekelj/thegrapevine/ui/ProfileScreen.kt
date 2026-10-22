package si.timrekelj.thegrapevine.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import si.timrekelj.thegrapevine.ApiError
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.BuildConfig
import si.timrekelj.thegrapevine.Config
import si.timrekelj.thegrapevine.NotificationPreferences

@Composable
fun ProfileScreen(app: AppState, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(app.profile?.display_name.orEmpty()) }
    var savingName by remember { mutableStateOf(false) }
    var nameSaved by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var preferences by remember { mutableStateOf<NotificationPreferences?>(null) }
    var savingPreferences by remember { mutableStateOf(false) }
    var preferenceError by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }

    suspend fun loadPreferences() {
        preferences = null
        preferenceError = null
        try { preferences = app.loadNotificationPreferences() }
        catch (e: Exception) { preferenceError = if (e is ApiError) e.message else "Could not load notification preferences. Please try again." }
    }

    fun savePreferences(updated: NotificationPreferences) {
        val previous = preferences ?: return
        preferences = updated
        savingPreferences = true
        preferenceError = null
        scope.launch {
            try { preferences = app.saveNotificationPreferences(updated) }
            catch (e: Exception) {
                preferences = previous
                preferenceError = if (e is ApiError) e.message else "Could not save notification preferences. Your previous choices have been kept. Please try again."
            }
            savingPreferences = false
        }
    }

    fun open(intent: Intent) {
        linkError = null
        try { context.startActivity(intent) }
        catch (_: Exception) { linkError = "No app is available to open this link." }
    }

    LaunchedEffect(app.group?.id) {
        name = app.profile?.display_name.orEmpty()
        loadPreferences()
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text("Your Corner", style = nameplate(44), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            DoubleRule()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            app.profile?.let { Avatar(it.id, 60.dp) }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(app.profile?.display_name ?: "Your account", style = display(26))
                Text(app.session?.email.orEmpty(), style = MetaStyle)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHead("Your name")
            Text("This is how you appear to your friends.", style = MetaStyle)
            PaperField(name, { name = it; nameSaved = false; nameError = null }, "Display name", enabled = !savingName)
            val canSave = !savingName && name.trim().isNotEmpty() && name.trim().length <= 80 && name.trim() != app.profile?.display_name
            InkButton(if (savingName) "Saving…" else "Save name", enabled = canSave) {
                savingName = true
                nameError = null
                scope.launch {
                    try {
                        app.saveDisplayName(name)
                        name = app.profile?.display_name ?: name
                        nameSaved = true
                    } catch (e: Exception) { nameError = if (e is ApiError) e.message else "Could not save your name. Please try again." }
                    savingName = false
                }
            }
            if (name.trim().length > 80) Text("Use a name up to 80 characters.", color = MaterialTheme.colorScheme.error, style = MetaStyle)
            if (nameSaved) Text("Name saved", style = MetaStyle)
            nameError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHead("Notifications")
            Text("For ${app.group?.name ?: "your paper"}. Changes save automatically.", style = MetaStyle)
            val current = preferences
            if (current != null) {
                PreferenceToggle("Mute this paper", "Pause all notifications from this paper.", current.muted, !savingPreferences) {
                    savePreferences(current.copy(muted = it))
                }
                Hairline()
                val enabled = !savingPreferences && !current.muted
                PreferenceToggle("New stories", "When friends bring something new to the paper.", current.new_stories, enabled) {
                    savePreferences(current.copy(new_stories = it))
                }
                PreferenceToggle("Replies to you", "When someone replies to your message.", current.direct_replies, enabled) {
                    savePreferences(current.copy(direct_replies = it))
                }
                PreferenceToggle("Mentions", "When someone mentions you in a conversation.", current.mentions, enabled) {
                    savePreferences(current.copy(mentions = it))
                }
            } else if (preferenceError == null) {
                CircularProgressIndicator(color = Press.ink, strokeWidth = 2.dp)
                Text("Loading preferences…", style = MetaStyle)
            }
            if (savingPreferences) Text("Saving…", style = MetaStyle)
            preferenceError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (preferences == null) TextAction("Try again") { scope.launch { loadPreferences() } }
            }
            Text("Push notifications also need permission on this device.", style = MetaStyle)
            SettingsLink("Device notification settings") {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHead("About & support")
            SettingsLink("Help & account requests") { open(Intent(Intent.ACTION_VIEW, "mailto:hello@thegrapevine.si".toUri())) }
            SettingsLink("Privacy policy") { open(Intent(Intent.ACTION_VIEW, "${Config.webURL}/privacy".toUri())) }
            SettingsLink("Terms of service") { open(Intent(Intent.ACTION_VIEW, "${Config.webURL}/terms".toUri())) }
            Text("The Grapevine · Version ${BuildConfig.VERSION_NAME}", style = MetaStyle)
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        TextButton(onClick = { onSignOut(); app.signOut() }, enabled = !savingName && !savingPreferences) {
            Text("Sign out", style = MetaStyle.copy(color = Press.danger, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun PreferenceToggle(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = NameStyle)
            Text(detail, style = MetaStyle.copy(lineHeight = 18.sp))
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Press.paper, checkedTrackColor = Press.ink, checkedBorderColor = Press.ink,
                uncheckedThumbColor = Press.ink3, uncheckedTrackColor = Press.paper, uncheckedBorderColor = Press.rule,
            ),
        )
    }
}

@Composable
private fun SettingsLink(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = BodyStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Press.ink)
        }
    }
}
