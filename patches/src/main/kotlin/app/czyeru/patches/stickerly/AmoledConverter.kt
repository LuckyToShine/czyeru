package app.czyeru.patches.stickerly

import kotlin.math.abs

internal object AmoledConverter {
    fun convertHexColor(rawHex: String, nameOrAttr: String = ""): String? {
        val cleaned = rawHex.trim().lowercase()
        if (!cleaned.startsWith("#")) return null

        val hexDigits = cleaned.removePrefix("#")
        val argb: Int = when (hexDigits.length) {
            3 -> {
                val r = hexDigits[0].digitToIntOrNull(16) ?: return null
                val g = hexDigits[1].digitToIntOrNull(16) ?: return null
                val b = hexDigits[2].digitToIntOrNull(16) ?: return null
                (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
            }
            6 -> (0xFF000000L or (hexDigits.toLongOrNull(16) ?: return null)).toInt()
            8 -> (hexDigits.toLongOrNull(16) ?: return null).toInt()
            else -> return null
        }

        val a = (argb ushr 24) and 0xFF
        if (a == 0) return null

        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        // Keep brand/accent colors untouched
        if (abs(r - g) > 18 || abs(g - b) > 18 || abs(r - b) > 18) {
            return null
        }

        val isTextOrIcon = nameOrAttr.contains("text", ignoreCase = true) ||
            nameOrAttr.contains("tint", ignoreCase = true) ||
            nameOrAttr.contains("title", ignoreCase = true) ||
            nameOrAttr.contains("subtitle", ignoreCase = true) ||
            nameOrAttr.contains("caption", ignoreCase = true) ||
            nameOrAttr.contains("icon", ignoreCase = true) ||
            nameOrAttr.contains("fg", ignoreCase = true)

        val lum = (r + g + g + b) shr 2

        return when {
            lum >= 245 && !isTextOrIcon -> String.format("#%02x000000", a)
            lum in 220..244 && !isTextOrIcon -> String.format("#%02x121212", a)
            lum in 195..219 && !isTextOrIcon -> String.format("#%02x1e1e1e", a)
            lum <= 45 && isTextOrIcon -> String.format("#%02xffffff", a)
            lum in 46..95 && isTextOrIcon -> String.format("#%02xcccccc", a)
            else -> null
        }
    }
}