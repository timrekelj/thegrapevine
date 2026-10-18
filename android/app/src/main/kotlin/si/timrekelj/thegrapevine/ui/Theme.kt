package si.timrekelj.thegrapevine.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.R

/** One grape ink on paper — the same tokens as `Theme.swift` and `globals.css`. */
object Grape {
    val paper = Color(0xFFF7F4EE)
    val raised = Color(0xFFFFFDF9)
    val ink = Color(0xFF4A2160)
    val ink2 = Color(0xFF75657C)
    val pink = Color(0xFFFF6AA6)
    val onPink = Color(0xFF2E1340)
    val rule = Color(0x294A2160)
}

val Gloock = FontFamily(Font(R.font.gloock_regular))

fun display(size: Int) = TextStyle(
    fontFamily = Gloock,
    fontSize = size.sp,
    lineHeight = (size * 1.08f).sp,
    letterSpacing = (-0.01).em,
    color = Grape.ink,
)

val MetaStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Grape.ink2)
val LabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.07.em, color = Grape.ink2)
val BodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = Grape.ink)

@Composable
fun GrapevineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Grape.ink,
            onPrimary = Grape.paper,
            secondary = Grape.pink,
            onSecondary = Grape.onPink,
            background = Grape.paper,
            onBackground = Grape.ink,
            surface = Grape.paper,
            onSurface = Grape.ink,
            surfaceVariant = Grape.raised,
            onSurfaceVariant = Grape.ink2,
            surfaceContainer = Grape.raised,
            surfaceContainerLow = Grape.paper,
            surfaceContainerHigh = Grape.raised,
            secondaryContainer = Grape.ink.copy(alpha = 0.12f),
            onSecondaryContainer = Grape.ink,
            outline = Grape.rule,
            error = Color(0xFF9A3149),
        ),
        content = content,
    )
}
