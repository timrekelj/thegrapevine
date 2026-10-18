package si.timrekelj.thegrapevine

import kotlin.math.roundToInt

/**
 * The halftone plate: every source image is printed in one ink rather than shown
 * in colour. Mirrors `Plate.swift` — grayscale, then map black to grape ink and
 * white to paper.
 *
 * The mapping is kept as plain arithmetic (not an `android.graphics.ColorMatrix`)
 * so it can be unit-tested without an Android runtime.
 */
object Plate {

    /** Rec.601 luminance — the weights `ColorMatrix.setSaturation(0)` uses. */
    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    private const val INK_R = 74f
    private const val INK_G = 33f
    private const val INK_B = 96f
    private const val PAPER_R = 247f
    private const val PAPER_G = 244f
    private const val PAPER_B = 238f

    /** 4x5 row-major, in Android's `ColorMatrixColorFilter` scale (translation 0..255). */
    val channels: FloatArray
        get() = floatArrayOf(
            (PAPER_R - INK_R) / 255f * LR, (PAPER_R - INK_R) / 255f * LG, (PAPER_R - INK_R) / 255f * LB, 0f, INK_R,
            (PAPER_G - INK_G) / 255f * LR, (PAPER_G - INK_G) / 255f * LG, (PAPER_G - INK_G) / 255f * LB, 0f, INK_G,
            (PAPER_B - INK_B) / 255f * LR, (PAPER_B - INK_B) / 255f * LG, (PAPER_B - INK_B) / 255f * LB, 0f, INK_B,
            0f, 0f, 0f, 1f, 0f,
        )

    /** What one pixel becomes: black prints as ink, white prints as paper. */
    fun toInk(red: Int, green: Int, blue: Int): IntArray {
        val luminance = LR * red + LG * green + LB * blue
        return intArrayOf(
            (INK_R + (PAPER_R - INK_R) * luminance / 255f).roundToInt().coerceIn(0, 255),
            (INK_G + (PAPER_G - INK_G) * luminance / 255f).roundToInt().coerceIn(0, 255),
            (INK_B + (PAPER_B - INK_B) * luminance / 255f).roundToInt().coerceIn(0, 255),
        )
    }
}
