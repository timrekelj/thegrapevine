package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.R

/**
 * The vine colour roles (design/DIRECTION.md, Tokens). Some keep older names: `paper` is the ground,
 * and `red` is the vine green, which only ever means "new to you".
 */
@Immutable
data class Palette(
    val paper: Color,
    val raised: Color,
    /** Fields, the composer, quoted replies and placeholder tiles. */
    val raised2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val tint: Color,
    val red: Color,
    val onRed: Color,
    /** The soft fill behind a "new" line. */
    val vineSoft: Color,
    /** The ground of the catch-up card, the paper's own writing. */
    val catchUp: Color,
    val danger: Color,
    val isDark: Boolean,
)

/** The light palette (leaf-white ground). */
val MorningEdition = Palette(
    paper = Color(0xFFF0F4EB),
    raised = Color(0xFFFFFFFF),
    raised2 = Color(0xFFF6F8F3),
    ink = Color(0xFF15221B),
    ink2 = Color(0xFF3F4C45),
    ink3 = Color(0xFF5E6B63),
    rule = Color(0x1415221B),
    tint = Color(0x0F15221B),
    red = Color(0xFF1E7A39),
    onRed = Color(0xFFFFFFFF),
    vineSoft = Color(0xFFDCEFE0),
    catchUp = Color(0xFF15221B),
    danger = Color(0xFFB42318),
    isDark = false,
)

/** The dark palette (night on the vine). */
val EveningEdition = Palette(
    paper = Color(0xFF0E1711),
    raised = Color(0xFF172219),
    raised2 = Color(0xFF1F2C22),
    ink = Color(0xFFEEF3EA),
    ink2 = Color(0xFFB5C1B8),
    ink3 = Color(0xFF8C9A90),
    rule = Color(0x1AEEF3EA),
    tint = Color(0x14EEF3EA),
    red = Color(0xFF4CC46F),
    onRed = Color(0xFF0E1711),
    vineSoft = Color(0xFF1D3A26),
    catchUp = Color(0xFF1D3A26),
    danger = Color(0xFFFF7A6E),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { MorningEdition }

/** The current palette's roles, read where they're drawn so dark mode follows the system. */
object Press {
    val paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper
    val raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
    val raised2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised2
    val ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val ink2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink2
    val ink3: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink3
    val rule: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rule
    val tint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.tint
    val red: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.red
    val onRed: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onRed
    val vineSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.vineSoft
    val catchUp: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.catchUp
    val danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
}

/** Bricolage Grotesque, bundled: titles and section heads. Everything else is set in [UiText]. */
val VineDisplay = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold),
)

/** The platform's sans for reading and controls (Google Sans Flex where the system has it). */
val UiText = FontFamily.SansSerif

/** A title in the display face: tight and heavy at headline sizes, a touch looser for row titles. */
@Composable
@ReadOnlyComposable
fun display(size: Int) = TextStyle(
    fontFamily = VineDisplay,
    fontWeight = if (size >= 26) FontWeight.ExtraBold else FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * (if (size >= 26) 1.04f else 1.15f)).sp,
    letterSpacing = (if (size >= 26) -0.02 else -0.015).em,
    color = Press.ink,
)

/** A section title, like "The conversation": the display face at row-title size. */
val SectionStyle: TextStyle @Composable @ReadOnlyComposable get() = display(19)

/** Times, categories, counts. */
val MetaStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = UiText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, color = Press.ink2)

/** A sentence-case label over a group of controls, like "Sources · 4". */
val LabelStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = UiText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = Press.ink2)

val BodyStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = UiText, fontSize = 17.sp, lineHeight = 23.sp, color = Press.ink)

/** Names and controls. */
val NameStyle: TextStyle @Composable @ReadOnlyComposable get() =
    TextStyle(fontFamily = UiText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Press.ink)

@Composable
fun GrapevineTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) EveningEdition else MorningEdition
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    // Our own surfaces sink when pressed (see Motion.kt); what Material still draws (app bars,
    // menus, switches) gets a faint ink ripple rather than a grey flood.
    val ripple = RippleConfiguration(color = p.ink, rippleAlpha = RippleAlpha(0.08f, 0.06f, 0.05f, 0.10f))
    CompositionLocalProvider(LocalPalette provides p, LocalRippleConfiguration provides ripple) {
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
