/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/compat/AppCompatibilities.kt
 *
 * Central Morphe `Compatibility` metadata so Morphe Manager shows human-readable app names.
 */
package app.czyeru.patches.shared.compat

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object AppCompatibilities {
    private const val TIKTOK_COLOR = 0xFE2C55

    /**
     * Target: TikTok global package, 47.4.3 and 47.2.41. Every TikTok fingerprint resolves on both
     * builds, so neither is dropped when the newer one is added.
     */
    fun tiktok(): Array<Compatibility> = arrayOf(
        Compatibility(
            name = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            appIconColor = TIKTOK_COLOR,
            targets = listOf(AppTarget("47.4.3"), AppTarget("47.2.41")),
        ),
    )
}
