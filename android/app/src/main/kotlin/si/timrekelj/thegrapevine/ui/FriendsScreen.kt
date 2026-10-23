package si.timrekelj.thegrapevine.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Config

@Composable
fun FriendsScreen(app: AppState) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("The Readers", style = nameplate(44), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                DoubleRule()
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text("${app.members.size} AT THE TABLE", style = LabelStyle.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                    Text((app.group?.name ?: "Your paper").uppercase(), style = LabelStyle.copy(fontSize = 10.sp, color = Press.ink2), maxLines = 1)
                }
                Hairline(color = Press.ink)
            }
        }
        items(app.members, key = { it.user_id }) { member ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(member.user_id, 42.dp)
                Text(member.profiles.display_name, style = display(21), modifier = Modifier.weight(1f))
                if (member.role == "owner") Text("EDITOR", style = LabelStyle.copy(fontSize = 10.sp), modifier = Modifier.border(1.dp, Press.ink).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Hairline()
        }
        app.group?.let { group ->
            item {
                Column(
                    Modifier.padding(top = 32.dp).fillMaxWidth().border(1.dp, Press.ink).padding(5.dp).border(1.dp, Press.ink).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("NOTICE", style = LabelStyle)
                    Text("Make room for a friend", style = display(26), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Good papers start with good company. Share an invitation to yours.", style = BodyStyle.copy(color = Press.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    Hairline()
                    Text("INVITE CODE", style = LabelStyle.copy(color = Press.ink2))
                    SelectionContainer {
                        Text(group.invite_code, style = MetaStyle.copy(fontSize = 18.sp, color = Press.ink, letterSpacing = 2.sp))
                    }
                    InkButton("Share invitation") {
                        val link = "${Config.webURL}/invite?code=${group.invite_code}"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, "Share invitation"))
                    }
                }
            }
        }
    }
}
