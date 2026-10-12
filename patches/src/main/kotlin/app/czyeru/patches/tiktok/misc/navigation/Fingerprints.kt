package app.czyeru.patches.tiktok.misc.navigation

import app.morphe.patcher.Fingerprint

/**
 * Static writer that stores the top tab node list (used by the tab strip and the
 * home ViewPager through HomeTabAbility.Yd2()) and the parallel TopTabProtocol
 * list (HomeTabAbility.K9()). Both lists are built in the same order and each
 * node's tag() returns its protocol's getTag().
 */
internal object TopTabListsWriterFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    name = "fp",
    returnType = "V",
    parameters = listOf(
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;",
        "L",
        "Ljava/util/List;",
        "Ljava/util/List;",
    ),
)

internal object BottomTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    name = "OB",
    returnType = "Ljava/util/List;",
    parameters = listOf(),
)
