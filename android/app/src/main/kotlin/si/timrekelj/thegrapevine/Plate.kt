package si.timrekelj.thegrapevine

import kotlin.math.sqrt

/**
 * The halftone plate: every photograph is printed in colour through a dot screen, as on the web
 * (`.plate` in globals.css). The photo shows only through the dots; each dot covers as much of its
 * cell as that part of the picture needs ink.
 *
 * On paper (light) the dark parts of a picture get the big dots; on the evening edition's black
 * paper the light parts do. The arithmetic is kept free of Android types so it can be unit-tested.
 */
object Plate {

    /** Dot pitch on screen, in dp — the same fine screen as the web's 2.4–2.8px. */
    const val PITCH_DP = 2.6f

    /** A solid's dot radius, as a fraction of the pitch: neighbours touch at 0.5, so solids just merge. */
    const val MAX_RADIUS = 0.56f

    /** Colour lost to the white between the dots is put back before printing. */
    const val SATURATION = 1.3f

    /** Rec.709 luminance of an sRGB pixel, 0 (black) to 1 (white). */
    fun luminance(red: Int, green: Int, blue: Int): Float =
        (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f

    /** Dot radius as a fraction of the pitch, for a cell of the given luminance. Dot area grows with the ink needed. */
    fun radius(luminance: Float, dark: Boolean): Float {
        val coverage = (if (dark) luminance else 1f - luminance).coerceIn(0f, 1f)
        return MAX_RADIUS * sqrt(coverage)
    }

    /** How many columns of dots a plate this wide gets. */
    fun columns(widthDp: Float): Int = (widthDp / PITCH_DP).toInt().coerceAtLeast(8)
}
