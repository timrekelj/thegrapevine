package si.timrekelj.thegrapevine.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Config

@Composable
fun FriendsScreen(app: AppState, onSignOut: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        item { Text("Friends", style = display(28), modifier = Modifier.padding(bottom = 16.dp)) }
        item { Text((app.group?.name ?: "Your paper").uppercase(), style = LabelStyle, modifier = Modifier.padding(bottom = 8.dp)) }
        items(app.members, key = { it.user_id }) { member ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(member.profiles)
                Text(member.profiles.display_name, style = BodyStyle, modifier = Modifier.weight(1f))
                if (member.role == "owner") Text("Owner", style = MetaStyle)
            }
            HorizontalDivider(color = Grape.rule, thickness = 0.5.dp)
        }
        app.group?.let { group ->
            item {
                Text("INVITE A FRIEND", style = LabelStyle, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
                SelectionContainer { Text(group.invite_code, style = display(21)) }
                TextButton(onClick = {
                    val link = "${Config.webURL}/invite?code=${group.invite_code}"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                    context.startActivity(Intent.createChooser(send, "Share invitation"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Grape.ink)
                    Spacer(Modifier.padding(4.dp))
                    Text("Share invitation", color = Grape.ink, fontSize = 15.sp)
                }
            }
        }
        item {
            TextButton(onClick = { onSignOut(); app.signOut() }, modifier = Modifier.padding(top = 20.dp)) {
                Text("Sign out", color = Grape.pink, fontSize = 15.sp)
            }
        }
    }
}
