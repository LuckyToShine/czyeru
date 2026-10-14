package app.czyeru.patches.stickerly

internal object AmoledColors {
    const val PURE_BLACK = "#ff000000"
    const val DARK_SURFACE = "#ff121212"
    const val DARK_SURFACE_VARIANT = "#ff1e1e1e"
    const val DARK_DIVIDER = "#ff242424"
    const val PURE_WHITE = "#ffffffff"
    const val LIGHT_GRAY_TEXT = "#ffb3b3b3"

    val namedColorReplacements = mapOf(
        "white" to PURE_BLACK,
        "white_100" to PURE_BLACK,
        "bg_white" to PURE_BLACK,
        "bg_primary" to PURE_BLACK,
        "bg_secondary" to "#ff0a0a0a",
        "bg_tertiary" to DARK_SURFACE,
        "bg_default" to PURE_BLACK,
        "bg_surface" to "#ff0a0a0a",
        "bg_elevated" to DARK_SURFACE,
        "bg_card" to "#ff0d0d0d",
        "bg_dialog" to DARK_SURFACE,
        "bg_bottom_sheet" to "#ff0d0d0d",
        "bg_input" to "#ff161616",
        "bg_search" to "#ff161616",
        "bg_tab" to PURE_BLACK,
        "bg_toolbar" to PURE_BLACK,
        "bg_statusbar" to PURE_BLACK,
        "bg_navigation" to PURE_BLACK,
        "design_default_color_background" to PURE_BLACK,
        "design_default_color_surface" to "#ff0a0a0a",
        "cardview_light_background" to "#ff0d0d0d",
        "gray_50" to "#ff0a0a0a",
        "gray_100" to DARK_SURFACE,
        "gray_150" to "#ff161616",
        "gray_200" to DARK_SURFACE_VARIANT,
        "gray_300" to "#ff262626",
        "grey_50" to "#ff0a0a0a",
        "grey_100" to DARK_SURFACE,
        "grey_200" to DARK_SURFACE_VARIANT,
        "grey_300" to "#ff262626",
        "black" to PURE_WHITE,
        "black_100" to PURE_WHITE,
        "text_primary" to PURE_WHITE,
        "text_secondary" to LIGHT_GRAY_TEXT,
        "text_tertiary" to "#ff808080",
        "text_hint" to "#ff666666",
        "text_black" to PURE_WHITE,
        "text_default" to PURE_WHITE,
        "text_title" to PURE_WHITE,
        "text_subtitle" to LIGHT_GRAY_TEXT,
        "text_body" to "#ffe6e6e6",
        "text_caption" to "#ff999999",
        "gray_800" to "#ffe0e0e0",
        "gray_850" to "#ffebebeb",
        "gray_900" to PURE_WHITE,
        "grey_800" to "#ffe0e0e0",
        "grey_900" to PURE_WHITE,
        "icon_primary" to PURE_WHITE,
        "icon_secondary" to LIGHT_GRAY_TEXT,
        "icon_default" to PURE_WHITE,
        "divider" to DARK_DIVIDER,
        "divider_color" to DARK_DIVIDER,
        "line_gray" to DARK_DIVIDER,
        "border_gray" to DARK_DIVIDER,
        "border_default" to DARK_DIVIDER
    )

    val narrowLiteralReplacements: Map<Int, Int> = mapOf(
        // Light backgrounds -> AMOLED Black / Dark Surfaces
        0xFFFAFAFA.toInt() to 0xFF000000.toInt(),
        0xFFF9F9F9.toInt() to 0xFF000000.toInt(),
        0xFFF8F9FA.toInt() to 0xFF000000.toInt(),
        0xFFF8F8F8.toInt() to 0xFF050505.toInt(),
        0xFFF5F5F5.toInt() to 0xFF0D0D0D.toInt(),
        0xFFF5F6F8.toInt() to 0xFF0D0D0D.toInt(),
        0xFFF4F5F7.toInt() to 0xFF0D0D0D.toInt(),
        0xFFF2F3F5.toInt() to 0xFF121212.toInt(),
        0xFFF1F3F5.toInt() to 0xFF121212.toInt(),
        0xFFF0F0F0.toInt() to 0xFF121212.toInt(),
        0xFFEFEFEF.toInt() to 0xFF141414.toInt(),
        0xFFEEEEEE.toInt() to 0xFF161616.toInt(),
        0xFFE9ECEF.toInt() to 0xFF1A1A1A.toInt(),
        0xFFE5E5E5.toInt() to 0xFF1F1F1F.toInt(),
        0xFFE0E0E0.toInt() to 0xFF242424.toInt(),
        0xFFDEE2E6.toInt() to 0xFF242424.toInt(),

        // Dark charcoal text -> Light text
        0xFF111111.toInt() to 0xFFFFFFFF.toInt(),
        0xFF191919.toInt() to 0xFFFFFFFF.toInt(),
        0xFF1A1A1A.toInt() to 0xFFFFFFFF.toInt(),
        0xFF202020.toInt() to 0xFFF5F5F5.toInt(),
        0xFF212121.toInt() to 0xFFF5F5F5.toInt(),
        0xFF212529.toInt() to 0xFFF5F5F5.toInt(),
        0xFF222222.toInt() to 0xFFF0F0F0.toInt(),
        0xFF333333.toInt() to 0xFFE0E0E0.toInt(),
        0xFF343A40.toInt() to 0xFFE0E0E0.toInt(),
        0xFF444444.toInt() to 0xFFCCCCCC.toInt(),
        0xFF495057.toInt() to 0xFFCCCCCC.toInt()
    )

    private fun packComposeColor(argb: Long): Long = (argb and 0xFFFFFFFFL) shl 32

    /**
     * Jetpack Compose Color(0xFF......) 64-bit ULong constants.
     * In Compose, sRGB colors are stored in the upper 32 bits of a 64-bit Long with 0x00000000 in the lower 32 bits.
     */
    val composeWideLiteralReplacements: Map<Long, Long> = buildMap {
        put(packComposeColor(0xFFFFFFFFL), packComposeColor(0xFF000000L))
        for ((src, dst) in narrowLiteralReplacements) {
            put(packComposeColor(src.toLong()), packComposeColor(dst.toLong()))
        }
    }
}