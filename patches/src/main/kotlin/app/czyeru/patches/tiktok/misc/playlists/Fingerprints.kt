package app.czyeru.patches.tiktok.misc.playlists

import app.morphe.patcher.Fingerprint

/** Span count of the favorite playlists grid (2 by default). */
internal object CollectionListSpanCountFingerprint : Fingerprint(
    definingClass = "/CollectionListMainUIAssem;",
    name = "Kp",
    returnType = "I",
    parameters = listOf(),
)

/** Builds the favorite playlists RecyclerView, its adapter and GridLayoutManager. */
internal object CollectionListViewCreatedFingerprint : Fingerprint(
    definingClass = "/CollectionListMainUIAssem;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

/**
 * Favorite playlists adapter (anonymous class inside CollectionListMainUIAssem).
 * Picks the header, the personal-homepage row cell or the standard cover card cell.
 */
internal object CollectionListCreateViewHolderFingerprint : Fingerprint(
    name = "onCreateBasicViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    strings = listOf("personal_homepage", "not match view type"),
)

/** Builds the RecyclerView of the "save to collection" sheet. */
internal object ChooseCollectionViewCreatedFingerprint : Fingerprint(
    definingClass = "/CollectionListChooseAssem;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

/** Bind method of the "save to collection" adapter: the only bind in a class holding CollectionListChooseAssem. */
internal object ChooseCollectionBindViewHolderFingerprint : Fingerprint(
    name = "onBindBasicViewHolder",
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", "I"),
    custom = { _, classDef ->
        classDef.fields.any { it.type.endsWith("/CollectionListChooseAssem;") } &&
            classDef.methods.any { it.name == "onCreateBasicViewHolder" }
    },
)

/** Bind method of the same adapter: the only bind in a class holding CollectionListMainUIAssem. */
internal object CollectionListBindViewHolderFingerprint : Fingerprint(
    name = "onBindBasicViewHolder",
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", "I"),
    custom = { _, classDef ->
        classDef.fields.any { it.type.endsWith("/CollectionListMainUIAssem;") } &&
            classDef.methods.any { it.name == "onCreateBasicViewHolder" }
    },
)
