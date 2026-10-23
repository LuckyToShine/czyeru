package app.czyeru.patches.tiktok.misc.navigation

import app.czyeru.util.getReference
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Static writer that stores the top tab node list (used by the tab strip and the
 * home ViewPager through HomeTabAbility.Yd2()) and the parallel TopTabProtocol
 * list (HomeTabAbility.K9()). Both lists are built in the same order and each
 * node's tag() returns its protocol's getTag().
 */
internal object TopTabListsWriterFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    // `fp` on 47.2.41, `Ip` on 47.4.3: the only static writer that takes the assem and both lists.
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;",
        "L",
        "Ljava/util/List;",
        "Ljava/util/List;",
    ),
)

/** `OB` on 47.2.41, `EB` on 47.4.3: the bottom tab list getter, the one that reads `BOTTOM_TAB`. */
internal object BottomTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.opcode == Opcode.SGET_OBJECT && it.getReference<FieldReference>()?.name == "BOTTOM_TAB"
        } == true
    },
)
