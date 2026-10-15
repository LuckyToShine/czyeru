package app.czyeru.patches.tiktok.misc.likes

import app.morphe.patcher.Fingerprint

/** Liked videos tab. Runs the base list setup first, so the list and its adapter exist at the end. */
internal object LikesViewCreatedFingerprint : Fingerprint(
    definingClass = "/ProfileLikesAwemeListFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

/** Liked videos tab onResume. Edits the list by position when un-liked videos are pending. */
internal object LikesResumeFingerprint : Fingerprint(
    definingClass = "/ProfileLikesAwemeListFragment;",
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

/**
 * Bind of the profile video grid adapter, shared by every profile tab.
 * It is the only adapter holding a CameoDraftInfo and an IExternalHeaderProvider.
 */
internal object ProfileGridBindViewHolderFingerprint : Fingerprint(
    name = "onBindBasicViewHolder",
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", "I"),
    custom = { _, classDef ->
        classDef.fields.any { it.type.endsWith("/CameoDraftInfo;") } &&
            classDef.fields.any { it.type.endsWith("/IExternalHeaderProvider;") }
    },
)
