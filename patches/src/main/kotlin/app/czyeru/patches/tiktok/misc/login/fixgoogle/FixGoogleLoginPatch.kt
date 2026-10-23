/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/login/fixgoogle/FixGoogleLoginPatch.kt
 */
package app.czyeru.patches.tiktok.misc.login.fixgoogle

import app.czyeru.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val fixGoogleLoginPatch = bytecodePatch(
    name = "Fix Google login",
    description = "Restores Google account sign-in after patching.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        listOf(GoogleAuthAvailableFingerprint.method).forEach { method ->
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }
    }
}

