package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Story
import si.timrekelj.thegrapevine.encodeBody

@Serializable
private data class SubmitBody(val group_id: String, val url: String, val comment: String?)

@Serializable
private data class SubmitResult(val status: String, val story_id: String? = null)

/**
 * Mirrors `ShareView.swift`. Also opened over other apps from the system share
 * sheet ([si.timrekelj.thegrapevine.ShareActivity]) with the shared link filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(app: AppState, onDismiss: () -> Unit, initialUrl: String = "", onOpenApp: (() -> Unit)? = null) {
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Press.paper,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FILING FOR ${(app.group?.name ?: "your paper").uppercase()}", style = LabelStyle.copy(color = Press.ink2))
                Text("Share a link", style = display(32))
            }
            DoubleRule()
            Text("Found something worth reading? Bring it to the table.", style = BodyStyle.copy(color = Press.ink2))
            val notice = when {
                !app.checked -> null
                app.session == null -> "Sign in to The Grapevine to share links with your paper."
                app.group == null -> "Join or start a paper in The Grapevine to share links."
                else -> null
            }
            if (notice != null) {
                Text(notice, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                onOpenApp?.let { InkButton("Open The Grapevine", onClick = it) }
                return@Column
            }
            Text("LINK", style = LabelStyle)
            PaperField(url, { url = it }, "https://…", keyboardType = KeyboardType.Uri)
            Text("YOUR COMMENT (OPTIONAL)", style = LabelStyle)
            PaperField(comment, { comment = it }, "Why it caught your eye", singleLine = false)
            app.group?.let { Text("Everyone in ${it.name} sees this with your name.", style = MetaStyle.copy(color = Press.ink3)) }
            result?.let { Text(it, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold)) }
            InkButton(if (busy) "Posting…" else "Share with your paper", enabled = !busy && url.isNotBlank() && app.group != null) {
                val group = app.group ?: return@InkButton
                scope.launch {
                    busy = true
                    result = null
                    result = try {
                        val submitted: SubmitResult = api.postObject(
                            "functions/v1/submit-source",
                            encodeBody(SubmitBody(group.id, url.trim(), comment.trim().ifEmpty { null })),
                        )
                        val story = submitted.story_id?.let { id ->
                            runCatching { api.getOne<Story>("rest/v1/stories", listOf("id" to "eq.$id")) }.getOrNull()
                        }
                        val title = story?.title ?: "the story"
                        when (submitted.status) {
                            "duplicate" -> "Your contribution was not added because it was a duplicate."
                            "joined" -> "Filed under $title."
                            "new_story" -> "New story: $title."
                            else -> "Posted."
                        }
                    } catch (e: Exception) {
                        e.message
                    }
                    busy = false
                }
            }
            if (result != null) {
                GhostButton("Done", onClick = onDismiss)
            }
        }
    }
}
