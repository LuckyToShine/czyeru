package app.czyeru.patches.uvm

/** Color rules of the UVM AMOLED patch. Colors are `[red, green, blue, alpha]` with channels from 0 to 255. */
internal object UvmColors {
    /** The dark background of the app's own dark theme, #0A0716. It becomes the chosen background. */
    private val REFERENCE = intArrayOf(0x0A, 0x07, 0x16)

    private const val MIN_SOLID_ALPHA = 0xC0
    private const val TEXT_MAX_LUMA = 0.30
    private const val TEXT_MAX_CHROMA = 24
    private const val SURFACE_MIN_LUMA = 0.88
    private const val SURFACE_MAX_CHROMA = 16

    private val REFERENCE_COLOR = Regex("#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?")
    private val ANY_COLOR = Regex("#([0-9a-fA-F]{3,8})")

    fun luma(r: Int, g: Int, b: Int): Double = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

    private fun chroma(c: IntArray) = maxOf(c[0], c[1], c[2]) - minOf(c[0], c[1], c[2])

    /** `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`, `black` and `white`. */
    fun parse(text: String): IntArray? {
        when (text) {
            "black" -> return intArrayOf(0, 0, 0, 255)
            "white" -> return intArrayOf(255, 255, 255, 255)
        }
        var digits = ANY_COLOR.matchEntire(text)?.groupValues?.get(1) ?: return null
        if (digits.length == 3 || digits.length == 4) digits = digits.map { "$it$it" }.joinToString("")
        if (digits.length != 6 && digits.length != 8) return null
        val alpha = if (digits.length == 8) digits.substring(6).toInt(16) else 255
        return intArrayOf(
            digits.substring(0, 2).toInt(16),
            digits.substring(2, 4).toInt(16),
            digits.substring(4, 6).toInt(16),
            alpha,
        )
    }

    /** A hard-coded text or icon color that is dark and not colorful. It disappears on a black screen. */
    fun isDarkText(text: String): Boolean {
        val c = parse(text) ?: return false
        return c[3] >= MIN_SOLID_ALPHA && luma(c[0], c[1], c[2]) < TEXT_MAX_LUMA && chroma(c) <= TEXT_MAX_CHROMA
    }

    /** A hard-coded background that is white or nearly white. */
    fun isLightSurface(text: String): Boolean {
        val c = parse(text) ?: return false
        return c[3] >= MIN_SOLID_ALPHA && luma(c[0], c[1], c[2]) >= SURFACE_MIN_LUMA && chroma(c) <= SURFACE_MAX_CHROMA
    }

    /**
     * The color [text] should have on the AMOLED background [base]: the background color of the app's
     * own dark theme, in any alpha, becomes [base]. Every other color stays, or null is returned.
     */
    fun background(text: String, base: IntArray): IntArray? {
        val m = REFERENCE_COLOR.matchEntire(text) ?: return null
        val hex = m.groupValues[1]
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return if (r == REFERENCE[0] && g == REFERENCE[1] && b == REFERENCE[2]) base.copyOf(3) else null
    }
}
