/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.misc.voicecomments

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.shared.lazyAbGetter

@Suppress("unused")
val enableVoiceCommentsPatch = bytecodePatch(
    name = "Enable voice comments",
    description = "Enables TikTok's native voice-comment recording and publishing entry points.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        // The publish gate unwraps the lazily read `audio_comment_publish` AB int and compares it to 1.
        lazyAbGetter("audio_comment_publish", "Z").addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
