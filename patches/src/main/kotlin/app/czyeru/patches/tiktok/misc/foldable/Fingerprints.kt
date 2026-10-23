package app.czyeru.patches.tiktok.misc.foldable

import app.morphe.patcher.Fingerprint

// The comment split owner is `X.0os0` on 47.2.41 and `X.089O` on 47.4.3; its log strings name it.
internal object CommentSplitLiveCheckFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;", "Landroid/content/res/Configuration;"),
    strings = listOf("isOptCommentSplit"),
)

internal object CommentSplitContainerCheckFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    strings = listOf("isOptSplitContainer"),
)
