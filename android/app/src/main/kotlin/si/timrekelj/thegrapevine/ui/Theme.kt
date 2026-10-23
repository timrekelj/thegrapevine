package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.R

/**
 * A black-and-white broadsheet, the same tokens as `web/app/globals.css`. Newsprint and ink carry
 * the page; colour comes only from the photographs and avatars. Press red is the one spot ink and
 * means one thing: new to you.
 */
@Immutable
data class Palette(
    val paper: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val tint: Color,
    val red: Color,
    val onRed: Color,
    val danger: Color,
    val isDark: Boolean,
)

val MorningEdition = Palette(
    paper = Color(0xFFF4F1EA),
    raised = Color(0xFFFBF9F4),
    ink = Color(0xFF161513),
    ink2 = Color(0xFF4A4740),
    ink3 = Color(0xFF75716A),
    rule = Color(0x29161513),
    tint = Color(0x0F161513),
    red = Color(0xFFC8231B),
    onRed = Color(0xFFFFFFFF),
    danger = Color(0xFFA3261F),
    isDark = false,
)

val EveningEdition = Palette(
    paper = Color(0xFF121110),
    raised = Color(0xFF1B1A17),
    ink = Color(0xFFEDE9E1),
    ink2 = Color(0xFFB9B3A8),
    ink3 = Color(0xFF8C877D),
    rule = Color(0x29EDE9E1),
    tint = Color(0x14EDE9E1),
    red = Color(0xFFFF5B4F),
    onRed = Color(0xFF1A0705),
    danger = Color(0xFFFF8A80),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { MorningEdition }

/** The current edition's inks, read where they're drawn so dark mode follows the system. */
object Press {
    val paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper
    val raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
    val ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val ink2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink2
    val ink3: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink3
    val rule: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rule
    val tint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.tint
    val red: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.red
    val onRed: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onRed
    val danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
}

/** The nameplate: blackletter, only for mastheads. */
val Nameplate = FontFamily(Font(R.font.unifraktur_maguntia))

/** Newsreader cut for headlines: the 72pt optical size. */
val NewsDisplay = FontFamily(Font(R.font.newsreader_display_semibold, FontWeight.SemiBold))

/** Newsreader cut for reading: the 16pt optical size. */
val News = FontFamily(
    Font(R.font.newsreader_text_regular, FontWeight.Normal),
    Font(R.font.newsreader_text_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.newsreader_text_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_text_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** Datelines, counts and labels. */
val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** A masthead in blackletter. */
@Composable
@ReadOnlyComposable
fun nameplate(size: Int) = TextStyle(fontFamily = Nameplate, fontSize = size.sp, lineHeight = (size * 1.05f).sp, color = Press.ink)

/** A headline: Newsreader display, tight. Small sizes use the text cut so they stay sturdy. */
@Composable
@ReadOnlyComposable
fun display(size: Int) = TextStyle(
    fontFamily = if (size >= 26) NewsDisplay else News,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * (if (size >= 26) 1.02f else 1.12f)).sp,
    letterSpacing = (if (size >= 26) -0.025 else -0.01).em,
    color = Press.ink,
)

/** An italic section head, like "The conversation". */
val SectionStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = News, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 28.sp, color = Press.ink)

val MetaStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, color = Press.ink2)

val LabelStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em, color = Press.ink)

val BodyStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = News, fontSize = 17.sp, lineHeight = 25.sp, color = Press.ink)

/** Names and controls stay in the plain system sans. */
val NameStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Press.ink)

@Composable
fun GrapevineTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) EveningEdition else MorningEdition
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = p.ink,
                onPrimary = p.paper,
                secondary = p.ink2,
                onSecondary = p.paper,
                background = p.paper,
                onBackground = p.ink,
                surface = p.paper,
                onSurface = p.ink,
                surfaceVariant = p.raised,
                onSurfaceVariant = p.ink2,
                surfaceContainer = p.raised,
                surfaceContainerLow = p.paper,
                surfaceContainerHigh = p.raised,
                surfaceContainerHighest = p.raised,
                secondaryContainer = p.tint,
                onSecondaryContainer = p.ink,
                outline = p.rule,
                outlineVariant = p.rule,
                error = p.danger,
            ),
            content = content,
        )
    }
}
