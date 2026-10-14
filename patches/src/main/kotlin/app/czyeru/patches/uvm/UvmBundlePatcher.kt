package app.czyeru.patches.uvm

/**
 * Makes the JavaScript bundle of Conexión Lince UVM 1.0.63 dark.
 *
 * The screens are drawn by React Native from the bundle, so the colors are not in `res/`. The app has a
 * complete dark theme, `uvmDark`, but nothing ever selects it: the root asks for the theme named after the
 * brand, `uvm`, which is the light one. This patch:
 *
 * 1. Makes the name `uvm` give the dark theme.
 * 2. Moves the backgrounds of that theme to the chosen AMOLED color.
 * 3. Fixes colors that are written into screens instead of read from the theme: dark text on a white
 *    background becomes light text on the AMOLED background.
 * 4. Gives the navigation container the colors of its own dark theme.
 * 5. Gives HTML text, which has no color of its own and is drawn black, a light default color.
 * 6. Keeps the box of the QR code of the school ID white. The code is dark and needs a light background.
 *
 * Every edit has the same length as what it replaces, so nothing moves. Every offset is checked first,
 * and the patch stops instead of guessing when the bundle is not the expected one.
 */
internal object UvmBundlePatcher {
    /** `sourceHash` of the bundle of version 1.0.63. */
    const val SOURCE_HASH = "d981940b08f4f321a1f72464cc6cd3244097a6b1"

    /** The function that builds all the themes, and the two that build the navigation themes. */
    private const val THEME_FUNCTION = 12919
    private const val THEME_FUNCTION_SIZE = 22437
    private const val NAVIGATION_DARK_FUNCTION = 10946
    private const val NAVIGATION_LIGHT_FUNCTION = 10948

    // Offsets inside THEME_FUNCTION.
    private const val LOAD_CONST_STRING = 115
    private const val PUT_NEW_OWN_BY_ID = 64
    private const val DARK_NAME_AT = 0x2FB4     // LoadConstString r21, 'uvm dark'
    private const val LIGHT_NAME_AT = 0x2865    // LoadConstString r21, 'uvm'
    private const val LIGHT_ENTRY_AT = 0x3A39   // PutNewOwnById r21, r34, 'uvm'
    private const val DARK_ENTRY_AT = 0x3A43    // PutNewOwnById r21, r31, 'uvmDark'
    private const val OBJECT_REGISTER = 21
    private const val LIGHT_THEME_REGISTER = 34
    private const val DARK_THEME_REGISTER = 31

    private val TEXT_KEYS = setOf("color", "tintColor", "textColor", "placeholderTextColor", "fill", "stroke")
    private val BACKGROUND_KEYS = setOf("backgroundColor")

    /** What the default navigation theme gets, by key. The background is the AMOLED color itself. */
    private val NAVIGATION_COLORS = mapOf(
        "primary" to "rgb(10, 132, 255)",
        "card" to "rgb(18, 18, 18)",
        "text" to "rgb(229, 229, 231)",
        "border" to "rgb(39, 39, 41)",
        "notification" to "rgb(255, 69, 58)",
    )
    private const val NAVIGATION_BACKGROUND_KEY = "background"

    /** The box behind the QR code of the school ID: `{ marginVertical, backgroundColor: 'white', borderRadius }`. */
    private val QR_BOX_KEYS = listOf("marginVertical", "backgroundColor", "borderRadius")

    /** Backgrounds of the dark theme that are a tint instead of the app's own #0A0716. */
    private val THEME_BACKGROUND_KEYS = setOf("Background", "background")
    private val THEME_BACKGROUND_TINTS = setOf("#121010", "#160707")

    /** Strings that stand for a light text color, best first. The HTML renderer gets the first it finds. */
    private val HTML_TEXT_COLORS = setOf("#fdfdff", "#FDFDFF", "#FFF", "#fff", "white")

    private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")

    /** What was done, for the log. */
    class Report(val backgrounds: Int, val rewrittenLiterals: Int, val sharedLiterals: Int, val htmlColor: String) {
        override fun toString() =
            "$backgrounds background string(s), $rewrittenLiterals literal(s) rewritten, " +
                "$sharedLiterals shared literal(s) left alone, HTML text $htmlColor"
    }

    private class Use(val function: Int, val key: String?, val text: String, val target: Int, val tag: Int)

    /** [background] is the AMOLED color as 0xRRGGBB. The bytes of [bundle] are edited in place. */
    fun apply(bundle: ByteArray, background: Int): Report {
        val base = intArrayOf((background shr 16) and 0xFF, (background shr 8) and 0xFF, background and 0xFF)
        val before = bundle.copyOf()
        val bytecode = HermesBundle(bundle)
        ensure(bytecode.sourceHash == SOURCE_HASH) {
            "This is not the bundle of Conexión Lince UVM 1.0.63 (source ${bytecode.sourceHash})"
        }

        val firstId = HashMap<String, Int>()
        val strings = Array(bytecode.stringCount) { bytecode.string(it) }
        strings.forEachIndexed { i, s -> firstId.putIfAbsent(s, i) }
        fun id(text: String) = firstId[text] ?: throw BundleException("The string '$text' is not in the bundle")

        val allowed = BooleanArray(bundle.size)
        fun allow(from: Int, to: Int) = (from until to).forEach { allowed[it] = true }

        selectDarkTheme(bytecode, ::id, ::allow)
        val backgrounds = setBackgroundStrings(bytecode, strings, base, ::allow)

        val textId = id("#fdfdff")
        val backgroundId = id("#0a0716")
        ensure(strings[textId] == "#fdfdff") { "The text color string is not as expected" }
        ensure(bytecode.string(backgroundId) == "#%02x%02x%02x".format(base[0], base[1], base[2])) {
            "The background string is not the chosen color"
        }
        val navigation = NAVIGATION_COLORS.mapValues { id(it.value) } + (NAVIGATION_BACKGROUND_KEY to backgroundId)

        val sites = bytecode.objectSites()
        val qrBoxes = sites.filter { site ->
            site.entries.map { it.first } == QR_BOX_KEYS &&
                site.entries[1].second.isString && strings[site.entries[1].second.value] == "white"
        }
        ensure(qrBoxes.size == 1) { "The box of the QR code was not found" }
        val qrBoxPosition = qrBoxes.single().entries[1].second.position

        val uses = HashMap<Int, MutableList<Use>>()
        var navigationValues = 0
        for (site in sites) {
            for ((key, value) in site.entries) {
                if (value.position == qrBoxPosition && site === qrBoxes.single()) continue
                // Only the theme has entries without a name, such as the numbered greys.
                if (!value.isString || (key == null && site.function != THEME_FUNCTION)) continue
                val text = strings[value.value]
                val target = when {
                    site.function == NAVIGATION_LIGHT_FUNCTION -> {
                        navigationValues++
                        navigation[key.orEmpty()] ?: throw BundleException("Unknown key '$key' in the navigation theme")
                    }
                    // The app's own dark theme. Backgrounds that are navy or brown tints become the AMOLED color.
                    site.function == THEME_FUNCTION ->
                        if (text == "#0A0716" || (key in THEME_BACKGROUND_KEYS && text in THEME_BACKGROUND_TINTS)) backgroundId else NONE
                    site.function == NAVIGATION_DARK_FUNCTION -> NONE
                    key in TEXT_KEYS && UvmColors.isDarkText(text) -> textId
                    key in BACKGROUND_KEYS && UvmColors.isLightSurface(text) -> backgroundId
                    else -> NONE
                }
                uses.getOrPut(value.position) { ArrayList() }.add(Use(site.function, key, text, target, value.tag))
            }
        }
        ensure(navigationValues == NAVIGATION_COLORS.size + 1) { "The navigation theme was not found" }
        // The stored white of the QR box must not be shared, or changing a style that shares it changes the box.
        ensure(qrBoxPosition !in uses) { "The white of the QR box is shared with another style" }

        var rewritten = 0
        var shared = 0
        val rewrittenPositions = HashSet<Int>()
        for ((position, users) in uses) {
            val targets = users.map { it.target }.toSet()
            if (targets == setOf(NONE)) continue
            if (targets.size > 1) {
                // One stored value serves several users. Changing it would change them all.
                ensure(users.none { it.function == NAVIGATION_LIGHT_FUNCTION }) { "The navigation theme is shared" }
                shared++
                continue
            }
            val target = targets.single()
            if (users[0].tag == Tag.SHORT_STRING) {
                ensure(target < 0x10000) { "String id $target does not fit" }
                bytecode.putU16(position, target)
                allow(position, position + 2)
            } else {
                bytecode.putU32(position, target)
                allow(position, position + 4)
            }
            rewrittenPositions.add(position)
            rewritten++
        }

        val htmlColor = lightenHtmlText(bytecode, sites, strings, ::id, rewrittenPositions, ::allow)

        allow(bundle.size - HermesBundle.FOOTER_SIZE, bundle.size)
        bytecode.refreshFooter()
        for (i in bundle.indices) {
            ensure(bundle[i] == before[i] || allowed[i]) { "Byte $i changed without being planned" }
        }
        return Report(backgrounds, rewritten, shared, htmlColor)
    }

    /**
     * The map of themes has an entry `uvm` that holds the light theme and an entry `uvmDark`. Point `uvm`
     * at the dark one, and give the dark theme the name `uvm`: the logo is chosen by comparing the name.
     */
    private fun selectDarkTheme(bytecode: HermesBundle, id: (String) -> Int, allow: (Int, Int) -> Unit) {
        val (start, size) = bytecode.function(THEME_FUNCTION)
        ensure(size == THEME_FUNCTION_SIZE) { "The theme function has changed" }
        val uvm = id("uvm")
        val darkName = id("uvm dark")
        val darkKey = id("uvmDark")

        val nameAt = start + DARK_NAME_AT
        ensure(bytecode.readU8(nameAt) == LOAD_CONST_STRING && bytecode.readU8(nameAt + 1) == OBJECT_REGISTER &&
            bytecode.readU16(nameAt + 2) == darkName) { "The name of the dark theme is not where expected" }
        val lightNameAt = start + LIGHT_NAME_AT
        ensure(bytecode.readU8(lightNameAt) == LOAD_CONST_STRING && bytecode.readU16(lightNameAt + 2) == uvm) {
            "The name of the light theme is not where expected"
        }

        val lightAt = start + LIGHT_ENTRY_AT
        val darkAt = start + DARK_ENTRY_AT
        ensure(bytecode.readU8(lightAt) == PUT_NEW_OWN_BY_ID && bytecode.readU8(darkAt) == PUT_NEW_OWN_BY_ID &&
            bytecode.readU8(lightAt + 1) == OBJECT_REGISTER && bytecode.readU8(darkAt + 1) == OBJECT_REGISTER &&
            bytecode.readU8(lightAt + 2) == LIGHT_THEME_REGISTER && bytecode.readU8(darkAt + 2) == DARK_THEME_REGISTER &&
            bytecode.readU16(lightAt + 3) == uvm && bytecode.readU16(darkAt + 3) == darkKey) {
            "The theme map is not where expected"
        }

        bytecode.putU16(nameAt + 2, uvm)
        allow(nameAt + 2, nameAt + 4)
        bytecode.bytes[lightAt + 2] = bytecode.bytes[darkAt + 2]
        allow(lightAt + 2, lightAt + 3)
    }

    /**
     * Edits the strings of the app's own dark background in the string table. Hermes stores strings so that
     * they overlap, for example the last letter of one is the first of the next. Letters that another string
     * also reads are kept, and the nearest color that still fits is used. Afterwards every string must read
     * as planned.
     */
    private fun setBackgroundStrings(bytecode: HermesBundle, strings: Array<String>, base: IntArray, allow: (Int, Int) -> Unit): Int {
        val bytes = bytecode.bytes
        val kinds = bytecode.stringKinds()
        val locations = Array(strings.size) { bytecode.stringLocation(it) }

        val ideal = sortedMapOf<Int, IntArray>()
        strings.forEachIndexed { i, s ->
            val channels = UvmColors.background(s, base) ?: return@forEachIndexed
            val current = UvmColors.parse(s)!!
            if (channels[0] == current[0] && channels[1] == current[1] && channels[2] == current[2]) return@forEachIndexed
            ensure(kinds[i] == 0 && !locations[i].utf16 && s.length == locations[i].length) { "Cannot edit the string '$s'" }
            ideal[i] = channels
        }

        // Bytes that a string we do not edit also reads stay as they are.
        val readers = HashMap<Int, MutableList<Int>>()
        locations.forEachIndexed { j, l ->
            for (p in l.offset until l.offset + l.byteLength) readers.getOrPut(p) { ArrayList() }.add(j)
        }
        val frozen = readers.filterValues { users -> users.any { it !in ideal } }.keys
        val decided = HashMap<Int, Int>()
        val result = HashMap<Int, String>()
        for ((i, channels) in ideal) {
            val offset = locations[i].offset
            val text = strings[i]
            val hex = text.substring(1, 7)
            val upper = hex.any { it.isLetter() } && hex.none { it.isLowerCase() }
            val digits = StringBuilder("#")
            channels.forEachIndexed { k, wanted ->
                val high = offset + 1 + 2 * k
                val low = offset + 2 + 2 * k
                val highFixed = high in frozen || high in decided
                val lowFixed = low in frozen || low in decided
                val highChar = (decided[high] ?: (bytes[high].toInt() and 0xFF)).toChar()
                val lowChar = (decided[low] ?: (bytes[low].toInt() and 0xFF)).toChar()
                var best = -1
                for (v in 0..255) {
                    if (highFixed && (v shr 4) != highChar.digitToInt(16)) continue
                    if (lowFixed && (v and 15) != lowChar.digitToInt(16)) continue
                    if (best < 0 || Math.abs(v - wanted) < Math.abs(best - wanted)) best = v
                }
                val pair = if (upper) "%02X".format(best) else "%02x".format(best)
                val newHigh = if (highFixed) highChar else pair[0]
                val newLow = if (lowFixed) lowChar else pair[1]
                decided[high] = newHigh.code
                decided[low] = newLow.code
                digits.append(newHigh).append(newLow)
            }
            result[i] = digits.append(text, 7, text.length).toString()
        }
        for ((i, text) in result) {
            for (k in text.indices) bytes[locations[i].offset + k] = text[k].code.toByte()
            allow(locations[i].offset, locations[i].offset + text.length)
        }
        for (i in strings.indices) {
            val got = bytecode.string(i)
            ensure(got == (result[i] ?: strings[i])) { "The string table changed unexpectedly at $i" }
            if (i in result) ensure(HEX_COLOR.matches(got)) { "The edited string '$got' is not a color" }
        }
        return result.count { (i, text) -> text != strings[i] }
    }

    /**
     * Text that has no color of its own is drawn black on this build of React Native, which is invisible on a
     * dark screen. The HTML renderer, which draws the news, notices and pop-ups of the school, has a default
     * style `{ fontSize, whiteSpace }` and narrows it to its `fontSize`. Point that style at an existing
     * `{ fontSize, color }` pair with a light color, and narrow it to `color`. Nothing is written but two
     * operands and one id, and all of them belong to these two literals.
     */
    private fun lightenHtmlText(
        bytecode: HermesBundle,
        sites: List<ObjectSite>,
        strings: Array<String>,
        id: (String) -> Int,
        rewritten: Set<Int>,
        allow: (Int, Int) -> Unit,
    ): String {
        val style = sites.filter { site ->
            site.entries.map { it.first } == listOf("fontSize", "whiteSpace") &&
                site.entries[1].second.isString && strings[site.entries[1].second.value] == "normal"
        }
        ensure(style.size == 1) { "The default style of the HTML renderer was not found" }
        val styleSite = style.single()
        ensure(styleSite.isLong && bytecode.readU16(styleSite.offset + 4) == 2) { "The default style is not as expected" }

        val picks = bytecode.arraySites().filter { site ->
            site.entries.size == 1 && site.entries[0].tag == Tag.BYTE_STRING && strings[site.entries[0].value] == "fontSize"
        }
        ensure(picks.size == 1) { "The style list of the HTML renderer was not found" }
        val pick = picks.single().entries[0]
        val fontSize = id("fontSize")
        val color = id("color")
        ensure(pick.value == fontSize && color < 0x100) { "The style list of the HTML renderer is not as expected" }

        val keyOffsets = sites
            .filter { !it.isLong && it.entries.map { entry -> entry.first } == listOf("fontSize", "color") }
            .map { it.keyOffset }
            .toSet()
        ensure(keyOffsets.size == 1) { "The keys fontSize and color were not found" }

        // A value list whose second value is a light color, that this patch does not rewrite.
        class Candidate(val rank: Int, val offset: Int, val color: String)
        val candidates = ArrayList<Candidate>()
        val runs = bytecode.valueRuns()
        runs.forEachIndexed { index, run ->
            val tag: Int
            val position: Int
            if (run.length >= 2) {
                tag = run.tag
                position = run.payload + bytecode.valueWidth(run.tag)
            } else if (index + 1 < runs.size) {
                tag = runs[index + 1].tag
                position = runs[index + 1].payload
            } else {
                return@forEachIndexed
            }
            if ((tag == Tag.LONG_STRING || tag == Tag.SHORT_STRING || tag == Tag.BYTE_STRING) && position !in rewritten) {
                val stringId = bytecode.stringIdAt(tag, position)
                if (stringId < strings.size && strings[stringId] in HTML_TEXT_COLORS) {
                    candidates.add(Candidate(if (run.tag == Tag.INTEGER) 0 else 1, run.offset, strings[stringId]))
                }
            }
        }
        ensure(candidates.isNotEmpty()) { "No light color for the HTML text was found" }
        val chosen = candidates.minWith(compareBy<Candidate>({ it.rank }, { it.offset }))

        bytecode.putU32(styleSite.offset + 6, keyOffsets.single())
        bytecode.putU32(styleSite.offset + 10, chosen.offset)
        allow(styleSite.offset + 6, styleSite.offset + 14)
        bytecode.bytes[pick.position] = color.toByte()
        allow(pick.position, pick.position + 1)
        return chosen.color
    }

    private const val NONE = -1
}
