package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import si.timrekelj.thegrapevine.*
import java.time.Instant

@Serializable
private data class DesignData(val group: Group, val members: List<Member>, val stories: List<StoryRow>)

/** Debug launch fixture for the production card components. It never creates an authenticated session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignPreviewScreen() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val data = remember { context.assets.open("design_fixture.json").bufferedReader().use { SupabaseJson.decodeFromString<DesignData>(it.readText()) } }
    MemberHues.record(data.members.map { it.user_id })
    CompositionLocalProvider(LocalMemberNames provides data.members.associate { it.user_id to it.profiles.display_name }) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            PaperScreenContent(
                groupName = data.group.name, members = data.members, stories = data.stories,
                seenAt = Instant.parse("2025-10-23T19:10:00Z"), loading = false, error = null, earlier = null,
                me = data.members.firstOrNull()?.profiles,
                onOpen = {}, onEarlier = {}, onPeople = {}, onAccount = {}, onShare = {}, onRefresh = {},
            )
        }
    }
}
