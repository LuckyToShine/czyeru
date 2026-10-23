/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.interaction.quickactions

import app.morphe.patcher.Fingerprint

/**
 * Decides whether the exposed quick emoji row is shown for a comment input
 * (`BaseSlotComponentTrigger` `(CommentContextSource, state)Z`, named `Jp` on 47.2.41 and `hq` on
 * 47.4.3, so only the signature is kept).
 */
internal object QuickCommentReactionGateFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/comment/keyboard/keyboardv2/refactor/ExposedEmojiPanelTrigger;",
    returnType = "Z",
    parameters = listOf("Lcom/ss/android/ugc/aweme/comment/model/CommentContextSource;", "L"),
)

internal object LongPressRepostGateFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/assem/digg/VideoDiggAssem;",
    returnType = "Z",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf(
        "Long press detected on digg button for aweme: ",
        "long_press_like_panel",
    ),
)
